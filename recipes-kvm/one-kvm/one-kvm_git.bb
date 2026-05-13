SUMMARY = "One-KVM StreamBox - open IP-KVM solution for Amlogic A311D2"
DESCRIPTION = "One-KVM is an open and lightweight IP-KVM solution written in Rust. \
This StreamBox variant integrates with Amlogic vfmcap + libmultienc for hardware-accelerated \
4K60 HDMI capture and H264/H265 encoding via the Wave521 VPU."
HOMEPAGE = "https://github.com/mofeng-git/One-KVM"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://LICENSE;md5=016b6c2875cfaf9d87362055aae3f974"

# Use the pushed One-KVM repository, pinned by SRCREV for reproducible builds.
SRC_URI = " \
    git://github.com/aml-streambox/One-KVM-StreamBox.git;protocol=https;branch=v0.6.2_dev \
    file://0001-cargo-use-rust-1.59-compatible-feature-names.patch \
    file://0002-axum-server-add-body-data-buf-bound.patch \
    file://one-kvm.service \
    file://one-kvm-vendor.tar.gz \
    file://one-kvm-web-dist.tar.gz \
    file://libyuv_stub.c \
"

CARGO_NETWORK_OFFLINE = "1"

# Use explicit SRCREV to avoid AUTOREV network fetch
SRCREV = "47d1cf27e99dbd1adfe98cc8ed792a80390c3226"

S = "${WORKDIR}/git"
CARGO_SRC_DIR = ""

inherit cargo systemd

# Use a pinned Yocto-managed native Rust/Cargo toolchain. The vendored One-KVM
# dependency graph uses Cargo feature syntax newer than kirkstone's Rust 1.59.
ONE_KVM_RUST_TOOLCHAIN = "${RECIPE_SYSROOT_NATIVE}${prefix}/lib/rust-official"
ONE_KVM_RUST_TARGET ?= "aarch64-unknown-linux-gnu"
CARGO = "${ONE_KVM_RUST_TOOLCHAIN}/bin/cargo"
RUSTC = "${ONE_KVM_RUST_TOOLCHAIN}/bin/rustc"
CARGO_BUILD_FLAGS = "-v --target ${ONE_KVM_RUST_TARGET} ${BUILD_MODE} --manifest-path=${MANIFEST_PATH}"
CARGO_TARGET_SUBDIR = "${ONE_KVM_RUST_TARGET}/${BUILD_DIR}"

# Do not pass kirkstone's Rust 1.59 libstd path to the newer upstream rustc.
RUSTFLAGS = "${RUST_DEBUG_REMAP}"

# Match the verified direct AML build: use the AML capture/encoder path and
# skip the generic V4L2 default feature.
CARGO_BUILD_FLAGS:append = " --no-default-features --features aml,hwencode"

python do_patch:prepend() {
    # Make vendored crates available before patching so recipe-local fixes can
    # be applied with normal BitBake patch handling.
    import os
    import shutil

    vendor_src = os.path.join(d.getVar('WORKDIR'), 'vendor')
    vendor_dst = os.path.join(d.getVar('S'), 'vendor')
    if os.path.isdir(vendor_src) and not os.path.exists(vendor_dst):
        shutil.copytree(vendor_src, vendor_dst, symlinks=True)

    axum_server_src = os.path.join(vendor_dst, 'axum-server')
    axum_server_dst = os.path.join(d.getVar('S'), 'patched-crates', 'axum-server')
    if os.path.isdir(axum_server_src) and not os.path.exists(axum_server_dst):
        os.makedirs(os.path.dirname(axum_server_dst), exist_ok=True)
        shutil.copytree(axum_server_src, axum_server_dst, symlinks=True)
}

# Native build dependencies
DEPENDS = " \
    rust-official-native \
    rust-llvm-native \
    pkgconfig-native \
    protobuf-native \
"

# Target build dependencies (libraries linked at runtime)
DEPENDS:append = " \
    libmultienc \
    libvfmcap \
    ffmpeg \
    vulkan-loader \
    libdrm \
    libjpeg-turbo \
    alsa-lib \
    libopus \
"

# Runtime dependencies
RDEPENDS:${PN} = " \
    libmultienc \
    libvfmcap \
    ffmpeg \
    vulkan-loader \
    libdrm \
    libjpeg-turbo \
    alsa-lib \
    libopus \
    systemd \
    udev \
    libusb1 \
    xz \
"

# The Amlogic 5.15 kernel builds USB gadget/configfs support into the kernel
# (CONFIG_USB_LIBCOMPOSITE=y, CONFIG_USB_F_HID=y, CONFIG_USB_F_MASS_STORAGE=y),
# so there are no kernel-module-* packages to depend on here.

# systemd service
SYSTEMD_SERVICE:${PN} = "one-kvm.service"
SYSTEMD_AUTO_ENABLE = "disable"

# The binary name produced by cargo
CARGO_BIN_NAME = "one-kvm"

do_compile:prepend() {
    if [ ! -x "${RUSTC}" ] || [ ! -x "${CARGO}" ]; then
        bbfatal "Managed Rust toolchain not found at ${ONE_KVM_RUST_TOOLCHAIN}"
    fi
    if [ ! -d "${ONE_KVM_RUST_TOOLCHAIN}/lib/rustlib/${ONE_KVM_RUST_TARGET}" ]; then
        bbfatal "Rust target ${ONE_KVM_RUST_TARGET} is not installed in ${ONE_KVM_RUST_TOOLCHAIN}"
    fi

    export PATH="${ONE_KVM_RUST_TOOLCHAIN}/bin:${PATH}"
    export RUSTC="${RUSTC}"

    if [ ! -f "${STAGING_LIBDIR_NATIVE}/llvm-rust/lib/libclang.so" ]; then
        bbfatal "libclang.so not found in Yocto native sysroot for bindgen"
    fi
    export LIBCLANG_PATH="${STAGING_LIBDIR_NATIVE}/llvm-rust/lib"

    CLANG_RESOURCE_INCLUDE=""
    for clang_include in "${LIBCLANG_PATH}"/clang/*/include; do
        if [ -d "${clang_include}" ]; then
            CLANG_RESOURCE_INCLUDE="${clang_include}"
            break
        fi
    done

    export BINDGEN_EXTRA_CLANG_ARGS="--sysroot=${STAGING_DIR_TARGET} -I${STAGING_INCDIR}"
    if [ -n "${CLANG_RESOURCE_INCLUDE}" ]; then
        export BINDGEN_EXTRA_CLANG_ARGS="${BINDGEN_EXTRA_CLANG_ARGS} -I${CLANG_RESOURCE_INCLUDE}"
    fi
    export V4L2R_VIDEODEV2_H_PATH="${STAGING_INCDIR}/linux"

    # RustEmbed embeds web/dist during release compilation. The frontend is
    # built outside BitBake and shipped as a small source artifact to avoid
    # networked npm resolution during Yocto builds.
    if [ -d "${WORKDIR}/web/dist" ]; then
        rm -rf "${S}/web/dist"
        cp -r "${WORKDIR}/web/dist" "${S}/web/"
    fi

    # The AML path does not use CPU libyuv conversion, but the hwencode feature
    # still links libyuv. Provide the same minimal static stub used by the direct
    # build until a target libyuv recipe exists.
    install -d "${WORKDIR}/one-kvm-support" "${STAGING_LIBDIR}/pkgconfig"
    ${CC} ${CFLAGS} -c "${WORKDIR}/libyuv_stub.c" -o "${WORKDIR}/one-kvm-support/libyuv_stub.o"
    ${AR} rcs "${STAGING_LIBDIR}/libyuv.a" "${WORKDIR}/one-kvm-support/libyuv_stub.o"
    cat > "${STAGING_LIBDIR}/pkgconfig/libyuv.pc" << EOF
prefix=/usr
libdir=\${prefix}/lib
includedir=\${prefix}/include

Name: libyuv
Description: Stub libyuv for One-KVM AML DMA-buf build path
Version: 0.0.0
Libs: -L\${libdir} -lyuv
Cflags:
EOF

    export PKG_CONFIG_PATH="${STAGING_LIBDIR}/pkgconfig:${PKG_CONFIG_PATH}"
    export PKG_CONFIG_ALLOW_CROSS="1"
    export ONE_KVM_LIBS_PATH="${STAGING_DIR_TARGET}/usr"
    export RUSTFLAGS="${RUSTFLAGS} -L native=${STAGING_LIBDIR}"

    # Overlay vendor directory from tarball onto source tree
    if [ -d "${WORKDIR}/vendor" ]; then
        cp -rn "${WORKDIR}/vendor" "${S}/"
    fi

    # Create .cargo/config.toml to point to vendored sources
    mkdir -p "${S}/.cargo"
    cat > "${S}/.cargo/config.toml" << EOF
[source.crates-io]
replace-with = "vendored-sources"

[source.vendored-sources]
directory = "${S}/vendor"
EOF

    # Overwrite the CARGO_HOME config to use our vendor instead of bitbake
    cat > "${CARGO_HOME}/config" << EOF
# EXTRA_OECARGO_PATHS
paths = []

[source.vendored-sources]
directory = "${S}/vendor"

[source.crates-io]
replace-with = "vendored-sources"
local-registry = "/nonexistent"

[http]
multiplexing = false
cainfo = "${RECIPE_SYSROOT_NATIVE}/etc/ssl/certs/ca-certificates.crt"

# Rust target
[target.${ONE_KVM_RUST_TARGET}]
linker = "${WORKDIR}/wrapper/target-rust-ccld"

# BUILD_SYS
[target.x86_64-unknown-linux-gnu]
linker = "${WORKDIR}/wrapper/build-rust-ccld"

[build]
target-dir = "${WORKDIR}/build/target"

[term]
progress.when = 'always'
progress.width = 80
EOF
}

do_install:append() {
    # Install the binary
    install -d ${D}${bindir}
    install -m 0755 ${B}/target/${CARGO_TARGET_SUBDIR}/one-kvm ${D}${bindir}/

    # Create working directories
    install -d ${D}${localstatedir}/lib/one-kvm
    install -d ${D}${sysconfdir}/one-kvm

    # Install default config if present
    if [ -f ${S}/config.toml ]; then
        install -m 0644 ${S}/config.toml ${D}${sysconfdir}/one-kvm/
    fi

    # Install systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/one-kvm.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${bindir}/one-kvm \
    ${localstatedir}/lib/one-kvm \
    ${sysconfdir}/one-kvm \
    ${systemd_system_unitdir}/one-kvm.service \
"

CONFFILES:${PN} = "${sysconfdir}/one-kvm/config.toml"

# Skip QA checks that don't apply to Rust binaries
INSANE_SKIP:${PN} += "already-stripped"

# Ensure the aml feature is only built for Amlogic machines
COMPATIBLE_MACHINE = "^(mesont7|mesont7c|mesong12b)"

# Allow cargo.bbclass to use vendored crates from the tarball
CARGO_DISABLE_BITBAKE_VENDORING = "0"

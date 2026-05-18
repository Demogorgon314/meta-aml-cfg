SUMMARY = "Official Rust/Cargo binary toolchain for native build tasks"
DESCRIPTION = "Installs a pinned upstream Rust toolchain into the native sysroot for recipes that need a newer Cargo than the Yocto distro toolchain provides."
LICENSE = "CLOSED"

RUST_OFFICIAL_VERSION = "${PV}"
RUST_OFFICIAL_HOST = "x86_64-unknown-linux-gnu"
RUST_OFFICIAL_TARGET = "aarch64-unknown-linux-gnu"
RUST_OFFICIAL_PREFIX = "${prefix}/lib/rust-official"

SRC_URI = " \
    https://static.rust-lang.org/dist/rustc-${RUST_OFFICIAL_VERSION}-${RUST_OFFICIAL_HOST}.tar.xz;name=rustc;subdir=rust-official-components \
    https://static.rust-lang.org/dist/cargo-${RUST_OFFICIAL_VERSION}-${RUST_OFFICIAL_HOST}.tar.xz;name=cargo;subdir=rust-official-components \
    https://static.rust-lang.org/dist/rust-std-${RUST_OFFICIAL_VERSION}-${RUST_OFFICIAL_HOST}.tar.xz;name=rust-std-host;subdir=rust-official-components \
    https://static.rust-lang.org/dist/rust-std-${RUST_OFFICIAL_VERSION}-${RUST_OFFICIAL_TARGET}.tar.xz;name=rust-std-target;subdir=rust-official-components \
"

SRC_URI[rustc.sha256sum] = "b049fd57fce274d10013e2cf0e05f215f68f6580865abc52178f66ae9bf43fd8"
SRC_URI[cargo.sha256sum] = "856962610ee821648cee32e3d6abac667af7bb7ea6ec6f3d184cc31e66044f6b"
SRC_URI[rust-std-host.sha256sum] = "36d7eacf46bd5199cb433e49a9ed9c9b380d82f8a0ebc05e89b43b51c070c955"
SRC_URI[rust-std-target.sha256sum] = "e9ac4ff3c87247a2195fcceddbf1bdeee5c4fd337f014d8f4c4e3ac99002021f"

S = "${WORKDIR}/rust-official-components"

inherit native

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d "${D}${RUST_OFFICIAL_PREFIX}"

    for installer in "${S}"/*/install.sh; do
        "${installer}" --prefix="${D}${RUST_OFFICIAL_PREFIX}" --disable-ldconfig
    done
}

SYSROOT_DIRS_NATIVE += "${RUST_OFFICIAL_PREFIX}"

# The native sysroot contains both host tools and target Rust std libraries.
# Host strip cannot process the target AArch64 shared objects.
INHIBIT_SYSROOT_STRIP = "1"

FILES:${PN} += "${RUST_OFFICIAL_PREFIX}"

INSANE_SKIP:${PN} += "already-stripped libdir staticdev"

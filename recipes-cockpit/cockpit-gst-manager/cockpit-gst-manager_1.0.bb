SUMMARY = "GStreamer management Cockpit plugin with AI-assisted pipeline generation"
DESCRIPTION = "A Cockpit plugin for managing multiple GStreamer streaming/encoding pipelines on Amlogic A311D2"
HOMEPAGE = "https://github.com/aml-streambox/cockpit-gst-manager"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=e4f899da08777ac405eece47fb5dff28"

SRC_URI = "git://github.com/aml-streambox/cockpit-gst-manager.git;protocol=https;branch=main"
SRCREV = "837c605465eb43411930c6770f4b8d02652699cf"

S = "${WORKDIR}/git"

inherit systemd

SYSTEMD_SERVICE:${PN} = "gst-manager.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = " \
    python3 \
    python3-dbus-next \
    python3-json \
    python3-asyncio \
    python3-logging \
    python3-aiohttp \
    cockpit \
    cockpit-bridge \
    cockpit-ws \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-bad \
    gstreamer1.0-rtsp-server-meta \
    gst-plugin-vfmcap \
"

do_install() {
    # Backend Python files
    install -d ${D}${libdir}/gst-manager
    cp -r ${S}/backend/* ${D}${libdir}/gst-manager/
    
    # Frontend Cockpit plugin
    install -d ${D}${datadir}/cockpit/gst-manager
    cp -r ${S}/frontend/* ${D}${datadir}/cockpit/gst-manager/
    
    # systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/yocto/files/gst-manager.service ${D}${systemd_system_unitdir}/
    
    # D-Bus policy file
    install -d ${D}${sysconfdir}/dbus-1/system.d
    install -m 0644 ${S}/yocto/files/org.cockpit.GstManager.conf ${D}${sysconfdir}/dbus-1/system.d/
    
    # Runtime directories
    install -d ${D}${localstatedir}/lib/gst-manager
    install -d ${D}${localstatedir}/lib/gst-manager/instances
    
    # Default config (empty or from samples)
    if [ -f ${S}/samples/config.json ]; then
        install -m 0600 ${S}/samples/config.json ${D}${localstatedir}/lib/gst-manager/
    else
        install -m 0600 -d ${D}${localstatedir}/lib/gst-manager
    fi
}

FILES:${PN} = " \
    ${libdir}/gst-manager \
    ${datadir}/cockpit/gst-manager \
    ${localstatedir}/lib/gst-manager \
    ${sysconfdir}/dbus-1/system.d \
"

CONFFILES:${PN} = "${localstatedir}/lib/gst-manager/config.json"

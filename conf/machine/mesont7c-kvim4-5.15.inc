#@TYPE: Machine
#@NAME: meson

#@DESCRIPTION: Machine configuration for meson systems

include conf/machine/include/amlogic_weston.inc
include conf/machine/include/amlogic_aiot_common.inc
include conf/machine/include/amlogic_aiot_nndemo.inc
include conf/machine/include/amlogic_enc_common.inc
include conf/machine/include/amlogic_nnstreamer.inc

require conf/machine/include/amlogic_gondul.inc

MACHINE_EXTRA_RRECOMMENDS += " \
        gpu \
        wififw-ap6275 \
        ap6xxx \
        adla-driver \
		coreutils \
        "

RDEPENDS:packagegroup-amlogic-baserootfs:append = " isp isp-dev"
RDEPENDS:packagegroup-amlogic-baserootfs:append = " cve libcve"
RDEPENDS:packagegroup-amlogic-baserootfs:append = " adla-lib"
RDEPENDS:packagegroup-amlogic-baserootfs:append = " npu-common npu-common-dev "
RDEPENDS:packagegroup-amlogic-baserootfs:append = " khadas-custom khadas-gsensor "

PREFERRED_VERSION_isp = "5.15"
PREFERRED_VERSION_ap6xxx = "5.15"
BOARD_NAME = "vim4"
CHIPSET_NAME = "A311D2"
KERNEL_DEVICETREE = "t7c_kvim4n.dtb t7_kvim4.dtb"
UBOOT_MACHINE = "t7c_kvim4n_config"
KERNEL_MODULE_AUTOLOAD += " dhd"
KERNEL_MODULE_PROBECONF += "dhd"
module_conf_dhd = "options dhd firmware_path=../../etc/wifi/6275/ nvram_path=../../etc/wifi/6275/nvram_ap6275s.txt"
KERNEL_MODULE_AUTOLOAD += " snd-soc-dummy_codec"
KERNEL_MODULE_AUTOLOAD += " snd-soc-aml_t9015"
KERNEL_MODULE_AUTOLOAD += " snd-soc-tas5707"
KERNEL_MODULE_AUTOLOAD += " snd-soc-aml_codec_tl1_acodec"

DISTRO_FEATURES:append = " aml-libjpeg"
DISTRO_FEATURES:append = " aml-libmultienc"
DISTRO_FEATURES:append = " disable-audio-server"
DISTRO_FEATURES:append = " aml-isp arm-isp "
DISTRO_FEATURES:append = " amlogic-tv "
DISTRO_FEATURES:append = " softap"

DISTRO_FEATURES:append = " mediactrlsrc-cam"
DISTRO_FEATURES:append = " mediactrlsrc-hdmi"
OVERRIDES .= ":an400:t7:onepass"
MACHINEOVERRIDES .= ":client"

DISTRO_FEATURES:append = " bt-bcm"
EXTRA_BLUETOOTH_STUFF := "${@bb.utils.contains('DISTRO_FEATURES', 'bluetooth', bb.utils.contains('DISTRO_FEATURES', 'bluez5', ' bluez5', ' bluez4', d), '', d)}"
EXTRA_BLUETOOTH_STUFF += " bluez-alsa"
EXTRA_BLUETOOTH_STUFF += " brcm-patchram-plus"
DISTRO_FEATURES:append = "${EXTRA_BLUETOOTH_STUFF}"

DISTRO_FEATURES:append = " wififw "

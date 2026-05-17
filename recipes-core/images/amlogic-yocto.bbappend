# Set default root password for Streambox devices
inherit extrausers

# Password: Streamb0x (SHA-512 hashed)
EXTRA_USERS_PARAMS = "usermod -p '\$6\$ZjHJv2dDBSOECMla\$yggyCuOgLMn0CchupaKf5zzwvj7pcAa2Ka2Zl2NhBN7/A38dfBmYt2SQdbD8ZinKc0Z5gT/2mEl.9Xi.Qbzff.' root;"

# Cockpit web admin interface with plugins
IMAGE_INSTALL:append = " cockpit cockpit-gst-manager"

# Keep opkg and its package database in the target image so local IPK updates
# can be installed directly on development boards.
IMAGE_FEATURES += "package-management"

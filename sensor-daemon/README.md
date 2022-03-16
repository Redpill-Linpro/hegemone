# Requirements
* Raspberry Pi Imager
* MicroSD Card + Reader on host PC
* Formatted empty SD card

# Installation
1. Install Raspberry Pi Imager
2. Run it and select 
   * Operating System: latest Raspberry Pi OS Lite OS (64-bit)
   * Storage : Internal SD card 
   
3. Select the settings gear and 
   * Configure Wi-Fi for your local network (2.4 Ghz!!)
   * enable SSH
   * set hostname
   * create an user and password
   * set locales

# First boot

Chuck the sdcard into the rpi, connect it to power, and pray it starts up!

Check your local router administrator page to find out the IP address
or try to ssh directly using `ssh <yourUser>@<hostname>.local`

Once in, open a root session (`sudo su`) and execute

```Bash
apt upgrade
apt install openjdk-17-jdk-headless i2c-tools -y
raspi-config
```

Go to Peripherals / Interfaces and select I2C and One-Wire protocols.
Enable, save, finish, reboot, reconnect.

Congratulations, you're now ready to start hacking.

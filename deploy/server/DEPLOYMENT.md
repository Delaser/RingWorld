# AndWhatNot RingWorld server

- Host: `andwhatnotstudio.com:25565`
- Minecraft: Java 1.21.11
- Fabric Loader: 0.19.3
- Fabric API: 0.141.4+1.21.11
- Geometry: 1,600-block circumference by 320-block width
- Default mode: creative / peaceful
- Service: `ringworld.service`
- Install directory: `/opt/ringworld-server`

Administrative commands:

```sh
systemctl status ringworld
journalctl -u ringworld -f
systemctl restart ringworld
systemctl stop ringworld
```

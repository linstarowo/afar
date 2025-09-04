## Afar
A faster, lighter chunk caching solution for minecraft. It can provide you with a rendering distance further than the server.
### Feature
- Listen and forge data packets at ClientPacketListener
- Using sqlite database to storage chunk data. Faster than traditional IO
- Retain the vanilla chunk loading, ensuring mod compatibility
### Dependence
- Minecraft SQLite JDBC [(modrinth)](https://modrinth.com/plugin/minecraft-sqlite-jdbc/version/3.49.1.0+2025-07-12)
- World ID Provider such as VoxelMap and JourneyMap ( Xaero's Map compatibility is still in production.)
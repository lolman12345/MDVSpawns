# MDVSpawns 1.0.0

Sistema propio de RandomSpawns para MDVCraft. Spawnea mobs de MythicMobs con límites reales y configurables.

## Requisitos

- Purpur/Paper 1.21.6
- Java 21
- MythicMobs instalado

## Carpetas

Al instalar genera:

```txt
/plugins/MDVSpawns/config.yml
/plugins/MDVSpawns/RandomSpawners/goblins.yml
```

Todos los `.yml` dentro de `RandomSpawners` son cargados como spawners.

## Comandos

```txt
/mdvspawns reload
/mdvspawns debug
/mdvspawns count
/mdvspawns list
/mdvspawns force <spawner> [jugador]
```

Permiso:

```txt
mdvspawns.admin
```

## Límites importantes

```yml
settings:
  global-limit: 180          # máximo total en el servidor
  per-player-limit: 15       # máximo cerca de cada jugador
  per-player-count-radius: 96
  per-chunk-limit: 3         # máximo por chunk
  min-distance-from-player: 36
  max-distance-from-player: 72
  avoid-any-player-min-distance: 32
```

## Chance

`chance` acepta dos formatos:

```yml
chance: 0.03 # 3%, estilo MythicMobs antiguo
chance: 3    # 3%, formato porcentaje directo
```

## Ejemplo de spawner

```yml
GoblinPeonBosque:
  enabled: true
  mythicmob: GoblinPeon
  chance: 0.04
  group-size: 1-2
  worlds:
    - world
  search-mode: SURFACE
  min-y: 46
  max-y: 140
  biomes:
    - FOREST
    - DARK_FOREST
  blocks:
    - GRASS_BLOCK
    - PODZOL
  max-nearby:
    radius: 48
    amount: 6
```

## Compilar

Sube este proyecto a GitHub y ejecuta el workflow `Build MDVSpawns`, o compila localmente con Maven:

```bash
mvn package
```

El jar queda en:

```txt
target/MDVSpawns-1.0.0.jar
```

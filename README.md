# Paranoiacachecache

Plugin **Paper 1.21.1** de cache-cache : une équipe de chasseurs traque les joueurs cachés sur une map configurable.

## Déroulement d'une partie

1. **Phase cachette** (`hide-time`, 120 s par défaut) — les cachés sont téléportés au centre de la map et se dispersent. Les chasseurs attendent, aveuglés, dans la waiting room. Chaque caché dispose de `player-pearl-uses` perle(s) de fuite et de viande crue.
2. **Phase recherche** (`hunt-time`, 600 s par défaut) — les chasseurs reçoivent leur kit (épée en diamant, arc Power, flèches, perle infinie à cooldown, Speed) et sont libérés. Toutes les `beep-delay` secondes, un bip trahit la position de chaque caché.
3. **Fin** — les chasseurs gagnent s'ils trouvent tout le monde ; sinon les cachés l'emportent au temps écoulé. Si personne n'a été trouvé, tout le monde brille 30 s avant la fin.

En fin de partie, les joueurs retrouvent leur position d'origine et la worldborder du monde est restaurée.

## Installation

```bash
mvn clean package
# -> target/cache-1.0.0-SNAPSHOT.jar dans le dossier plugins/ du serveur
```

Requiert Java 21 et un serveur Paper 1.21.1.

## Commandes

Toutes les commandes exigent la permission `cache.admin` (op par défaut).

| Commande | Effet |
|---|---|
| `/cache start` | Lance une partie |
| `/cache stop` | Arrête la partie en cours |
| `/cache reload` | Recharge config et messages |
| `/cache select <map>` | Sélectionne la map active |
| `/cache chasseur set <joueur>` | Désigne un chasseur |
| `/cache chasseur clear` / `list` | Vide / affiche la liste des chasseurs |
| `/cache time hide set <s>` | Change la durée de la phase cachette |
| `/cache time hunt set <s>` | Change la durée de la phase recherche |
| `/cache map create\|delete\|list <nom>` | Gestion des maps |
| `/cache map setcenter\|sethunterspawn\|setwaiting <nom>` | Définit un point sur votre position |
| `/cache map setborder <nom> <rayon>` | Rayon de la worldborder |

Une map n'est jouable que si centre, spawn chasseur, waiting room et rayon sont définis.

## Configuration

Voir `config.yml` (durées, kit chasseur, bips) et `messages.yml` (textes, codes couleur `&`).

`beep-sound` accepte une clé Minecraft (`block.note_block.pling`) ; les anciens noms d'enum (`BLOCK_NOTE_BLOCK_PLING`) restent tolérés.

## Compatibilité

Compilé contre `paper-api:1.21.1`. Pour viser une version plus récente, vérifier `Enchantment.POWER` et les `PotionEffectType`, passés en registres dans les versions ultérieures. Le son des bips utilise déjà l'API Adventure et ne pose pas de problème.

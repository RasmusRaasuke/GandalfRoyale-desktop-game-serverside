package ee.taltech.server.components;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import ee.taltech.server.GameServer;
import ee.taltech.server.entities.Item;
import ee.taltech.server.entities.Spell;
import ee.taltech.server.entities.PlayerCharacter;
import ee.taltech.server.entities.collision.CollisionListener;
import ee.taltech.server.network.messages.game.ItemDropped;
import ee.taltech.server.network.messages.game.ItemPickedUp;
import ee.taltech.server.network.messages.game.KeyPress;

import java.util.HashMap;
import java.util.Map;

public class Game {

    public final Lobby lobby;
    public final GameServer server;
    public final Integer gameId;
    public final Map<Integer, PlayerCharacter> alivePlayers;
    public final Map<Integer, PlayerCharacter> deadPlayers;
    public final Map<Integer, Spell> spells;
    public final Map<Integer, Item> items;
    private final World world;

    /**
     * Construct Game.
     *
     * @param server main GameServer
     * @param lobby given players that will be playing in this game
     */
    public Game(GameServer server, Lobby lobby) {
        world = new World(new Vector2(0, 0), true); // Create a new Box2D world
        CollisionListener collisionListener = new CollisionListener(this);
        world.setContactListener(collisionListener); // Set collision listener that detects collision

        this.server = server;
        this.lobby = lobby;
        this.gameId = lobby.lobbyId;
        this.alivePlayers = createPlayersMap();
        this.deadPlayers = new HashMap<>();
        this.spells = new HashMap<>();
        this.items = new HashMap<>();

        Item item1 = new Item(SpellTypes.FIREBALL, 20F, 20F);
        Item item2 = new Item(SpellTypes.FIREBALL, 30F, 20F);

        addItem(item1, null);
        addItem(item2, null);
    }

    /**
     * Get world where hit boxes exist.
     *
     * @return world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Set player's action based on what key they pressed.
     *
     * @param keyPress keyPress message
     * @param player player who pressed the key
     */
    public void setPlayerAction(KeyPress keyPress, PlayerCharacter player) {
        if (keyPress.action.equals(KeyPress.Action.DROP) && keyPress.extraField != null) {
                Item droppedItem = player.dropItem(keyPress.extraField);
                addItem(droppedItem, player);
        }
        if (keyPress.action.equals(KeyPress.Action.INTERACT)) {
            for (Item item : items.values()) {
                if (item.getCollidingWith().equals(player)) {
                    player.pickUpItem(item);
                    removeItem(item, player);
                }
            }
        }
    }

    /**
     * Create Map where all the player objects are in by their ID.
     * Also create hit box for player character.
     *
     * @return players Map
     */
    private Map<Integer, PlayerCharacter> createPlayersMap(){
        Map<Integer, PlayerCharacter> result = new HashMap<>(); // New Map

        for (Integer playerID : server.connections.keySet()) {
            if (lobby.players.contains(playerID)) { // If player ID is in lobby's players list
                server.connections.put(playerID, gameId);
                PlayerCharacter player = new PlayerCharacter(playerID); // Create character for player
                player.createHitBox(world); // Create hit box for player
                result.put(player.playerID, player);
            }
        }
        return result;
    }

    /**
     * Add new spell to game.
     *
     * @param spell new spell
     */
    public void addSpell(Spell spell) {
        spells.put(spell.getSpellId(), spell);
    }

    /**
     * Damage player and put them into deadPlayers if they have 0 hp
     *
     * @param id player ID
     * @param amount amount of damage done to player
     */
    public void damagePlayer(Integer id, Integer amount) {
        PlayerCharacter player = alivePlayers.get(id); // Get player

        int newHealth = Math.max(player.health - amount, 0); // Health can not be less than 0
        player.setHealth(newHealth); // 10 damage per hit

        // If player has 0 health move them to dead players
        if (player.health == 0) {
            deadPlayers.put(id, player);
        }
    }

    /**
     * Add item to game aka add dropped item.
     *
     * @param item item that is added
     * @param playerCharacter player character that dropped item if player dropped else null
     */
    public void addItem(Item item, PlayerCharacter playerCharacter) {
        if (playerCharacter != null) {
            item.setXPosition((float) playerCharacter.getXPosition());
            item.setYPosition((float) playerCharacter.getYPosition());
        }
        item.createBody(world);
        items.put(item.getId(), item);

        for (Integer playerId : alivePlayers.keySet()) {
            ItemDropped message;
            if (playerCharacter != null) {
                message = new ItemDropped(playerCharacter.getPlayerID(), item.getId(), item.getType(),
                        (float) playerCharacter.getXPosition(), (float) playerCharacter.getYPosition());
            } else {
                message = new ItemDropped(null, item.getId(), item.getType(),
                        item.getXPosition(), item.getYPosition());
            }
            server.server.sendToUDP(playerId, message);
        }
    }

    /**
     * Remove item from the game aka pick it up from the ground.
     *
     * @param item item that is removed
     * @param playerCharacter player character that picked item up if player picked up else null
     */
    public void removeItem(Item item, PlayerCharacter playerCharacter) {
        item.removeBody();
        items.remove(item.getId());

        for (Integer playerId : alivePlayers.keySet()) {
            ItemPickedUp message;
            if (playerCharacter != null) {
                message = new ItemPickedUp(playerCharacter.getPlayerID(), item.getId(), item.getType());
            } else {
                message = new ItemPickedUp(null, item.getId(), item.getType());
            }
            server.server.sendToUDP(playerId, message);
        }
    }
}

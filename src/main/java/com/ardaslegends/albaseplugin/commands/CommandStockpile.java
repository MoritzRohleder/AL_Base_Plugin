package com.ardaslegends.albaseplugin.commands;

import com.ardaslegends.albaseplugin.AL_Base_Plugin;
import com.ardaslegends.albaseplugin.alapiclients.ALApiClient;
import com.ardaslegends.albaseplugin.models.BackendModels.BackendFactionStockpileModel;
import com.ardaslegends.albaseplugin.models.BackendModels.BackendPlayerModel;
import com.ardaslegends.albaseplugin.resources.StockpileConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * All Methods and variables related to the Command Stockpile are within this class
 */
public class CommandStockpile implements CommandExecutor {

    private final FileConfiguration stockpileConfig =
            StockpileConfig.getStockpileConfig();
    private final String msgPrefix = AL_Base_Plugin.getMsgPrefix();
    private final String errorPrefix = AL_Base_Plugin.getErrorPrefix();
    private final ALApiClient apiClient = new ALApiClient();

    /**
     * onCommand is being run if the command stockpile was executed
     * The Syntax for the command is simple: /stockpile [info|stored|add] {faction}
     *
     * @param sender Source of the command
     * @param command Command which was executed
     * @param label Alias of the command which was used
     * @param args Passed command arguments
     * @return returns, whether the command was successful or not
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        //Checks if the sender of the command is actually a player
        if (sender instanceof Player) {
            Player player = (Player) sender;
            int heldItemSlot = player.getInventory().getHeldItemSlot();
            ItemStack heldItem =
                    player.getInventory().getItem(heldItemSlot);
            if (!AL_Base_Plugin.getBackendOnline()) {
                if (args.length == 2) {
                    if (args[0].equalsIgnoreCase("info")) {
                        String faction = args[1];
                        StringBuilder sbInfo = new StringBuilder();
                        createInfoMsg(heldItem, faction, sbInfo);
                        player.sendMessage(sbInfo.toString());
                        return true;
                    }
                }else {
                    player.sendMessage(errorPrefix + "The command only supports /stockpile info <faction> while the backend is down. Please contact the devs.");
                    return false;
                }
            }
            if (args.length > 2) {
                //If the command has to many arguments
                player.sendMessage(errorPrefix + "To many arguments.");
                return false;
            } else if (args.length == 2) {
                //If the command has 2 arguments, it could be the staff version of /stockpile stored
                if (args[0].equalsIgnoreCase("stored")) {
                    if (!AL_Base_Plugin.getBackendOnline()) {
                        sender.sendMessage(errorPrefix + "The Backend is offline, this command requires the Backend to be online. Please contact the devs.");
                        return true;
                    }
                    if (player.hasPermission("al.staff.stockpileStoredFaction")) {
                        BackendFactionStockpileModel factionStockpileModel = apiClient.getFactionStockpile(args[1].replaceAll("_", "%20"));
                        if (factionStockpileModel.getFactionName() != null) {
                            player.sendMessage(msgPrefix
                                               + "The faction "
                                               + factionStockpileModel.getFactionName()
                                               + " has a stockpile of "
                                               + factionStockpileModel.getAmount());
                        } else {
                            player.sendMessage(errorPrefix
                                               + "The faction "
                                               + args[1]
                                               + " might not exist");
                        }
                    } else {
                        player.sendMessage(errorPrefix
                                           + "You don´t have permission to run this command.");
                    }
                } else if (args[0].equalsIgnoreCase("info")) {
                    String faction = args[1];
                    StringBuilder sbInfo = new StringBuilder();
                    createInfoMsg(heldItem, faction, sbInfo);
                    player.sendMessage(sbInfo.toString());
                    return true;
                } else {
                    player.sendMessage(errorPrefix + "Wrong arguments.");
                    return false;
                }
            } else if (args.length == 1) {
                if (!AL_Base_Plugin.getBackendOnline()) {
                    sender.sendMessage(errorPrefix + "The Backend is offline, this command requires the Backend to be online. Please contact the devs.");
                    return true;
                }
                BackendPlayerModel playerModel = apiClient.getPlayerByIGN(player.getName());
                switch(args[0]) {
                    case "info":
                        StringBuilder sbInfo = new StringBuilder();
                        createInfoMsg(heldItem, playerModel.getFaction(), sbInfo);
                        player.sendMessage(sbInfo.toString());
                        break;
                    case "stored":
                        player.sendMessage(msgPrefix +
                                           "Your faction " +
                                           playerModel.getFaction() +
                                           " has " +
                                           getStored(playerModel.getFaction()) +
                                           " stored in the Stockpile");
                        break;
                    case "add":
                        subcmdAdd(player, playerModel, heldItem);
                        player.sendMessage(msgPrefix +
                                           "Your faction " +
                                           playerModel.getFaction() +
                                           " has " +
                                           getStored(playerModel.getFaction()) +
                                           " stored in the Stockpile");
                        break;
                    default:
                        player.sendMessage(errorPrefix + args[0] + " is no proper argument.");
                        return false;
                }
            } else {
                if (!AL_Base_Plugin.getBackendOnline()) {
                    sender.sendMessage(errorPrefix + "The Backend is offline, this command requires the Backend to be online. Please contact the devs.");
                    return true;
                }
                BackendPlayerModel playerModel = apiClient.getPlayerByIGN(player.getName());
                //If no argument was given, we assume /stockpile add
                subcmdAdd(player, playerModel, heldItem);
                player.sendMessage(msgPrefix +
                                   "Your faction " +
                                   playerModel.getFaction() +
                                   " now has " +
                                   getStored(playerModel.getFaction()) +
                                   " stored in the Stockpile");
            }
        } else {
            sender.sendMessage("This command can only be run by Players in-game.");
        }
        return true;
    }

    /**
     * As add is being assumed as standard command and run on following occasions
     * - /stockpile add
     * - /stockpile
     * The context of that command is put into this method
     * @param player the player running the command
     * @param playerModel a model of the player running the command
     * @param heldItem the item in the players hand
     */
    private void subcmdAdd(Player player, BackendPlayerModel playerModel, ItemStack heldItem) {
        StringBuilder sbAdd = new StringBuilder();
        double value = createInfoMsg(heldItem, playerModel.getFaction(), sbAdd);
        player.sendMessage(sbAdd.toString());
        if (value > 0) {
            BackendFactionStockpileModel factionStockpileModel =
                    new BackendFactionStockpileModel(playerModel.getFaction(), (int)value);
            int statusCode = apiClient.addStockpile(factionStockpileModel);
            switch (statusCode) {
                case 200:
                    player.sendMessage(msgPrefix
                                       + "You added "
                                       + value
                                       + " to your factions stockpile.");
                    player.getInventory().remove(heldItem);
                    break;
                default:
                    player.sendMessage(errorPrefix
                                       + "Something went wrong connecting to the backend. "
                                       + "Please contact Staff. "
                                       + "Status Code: "
                                       + statusCode);
                    break;
            }
        }
    }

    /**
     * Creates an Info Message using the given StringBuilder and returns the value,
     * that the item would have for the stockpile as double
     * @param item the item to be checked
     * @param factionName the faction the item is being checked for
     * @param infoMsgSb the StringBuilder, that will contain the Information Msg after this method
     * @return the value of the given item on the stockpile
     */
    private double createInfoMsg(ItemStack item, String factionName, StringBuilder infoMsgSb){
        double value = getStockpileValue(item, factionName);
        int error = (int) value;
        switch (error) {
            case -10: //Error-Code -10: The player is not holding an item
                infoMsgSb.append(errorPrefix)
                         .append("You need to hold an item when running this command");
                break;
            case -2: //Error-Code -2: Not a stack of 64
                infoMsgSb.append(errorPrefix)
                         .append("Only stacks of 64 are accepted for the stockpile");
                break;
            case -1: //Error-Code -1: Item not listed for stockpile usage
                infoMsgSb.append(errorPrefix)
                         .append("The item ")
                         .append(getItemName(item))
                         .append(" is not listed for stockpile usage");
                break;
            case -101: //Error-Code -101: Manflesh not usable for players faction
                infoMsgSb.append(errorPrefix)
                         .append("Your faction ")
                         .append(factionName)
                         .append(" can not use manflesh for the stockpile");
                break;
            default:
                infoMsgSb.append(msgPrefix)
                         .append("The item ")
                         .append(getItemName(item))
                         .append(" adds ")
                         .append(value)
                         .append(" to the stockpile");
        }
        return value;
    }

    /**
     * returns the value, that a given faction has stored in the stockpile.
     * @param factionName The faction, of which the stockpile value should be returned
     * @return the value stored in the stockpile
     */
    private double getStored(String factionName) {
        BackendFactionStockpileModel factionStockpileModel = apiClient.getFactionStockpile(factionName);
        return factionStockpileModel.getAmount();
    }

    /**
     * Calculates the stockpile-value of the given item based on the
     * stockpileConfig.yml
     * If an error occurs an error code is returned instead of the value
     * -1: The item is not listed for stockpile usage
     * -2: The stack is not a full stack
     * -10: The player has an empty hand
     * -101: manflesh is not allowed for the players faction
     *
     * @param item The item, of which the stockpile value is to be calculated
	 * @param factionName The faction, the item would be stored
     * @return the value for the stockpile, or an error code
     */
    private double getStockpileValue(ItemStack item, String factionName){
        if (item == null) {
            //If the player has an empty hand
            return -10;
        }
        if (item.getAmount() != 64) {
            //If the held stack is not a full stack of 64
            return -2;
        }
        //Get the name of the item
        String name = getItemName(item);
        if (!stockpileConfig.contains(name)) {
            //If the item is not listed for stockpile usage
            return -1;
        }
        //Checking restrictions on specific items
        {
            if (name.equalsIgnoreCase("LOTR_ITEMMANFLESH")) {
                //If the item is manflesh
                List<String> canUseManflesh = stockpileConfig.getStringList("can-use-manflesh");
                if (!canUseManflesh.contains(factionName)) {
                    //If the players faction can not use manflesh
                    return -101;
                }
            }
        }
        return stockpileConfig.getDouble(name) * item.getAmount();
    }

    /**
     * A Method, that returns the name of the Item given
     * @param item the Item of which the name is to be returned
     * @return the name of the item as String
     */
    private String getItemName(ItemStack item) {
        return item.toString().substring(item.toString().indexOf('{')+1, item.toString().indexOf(' '));
    }
}
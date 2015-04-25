package joshie.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import joshie.crafting.api.ITriggerData;
import joshie.crafting.helpers.NBTHelper;
import joshie.crafting.helpers.PlayerHelper;
import joshie.crafting.network.PacketHandler;
import joshie.crafting.network.PacketSyncAbilities;
import joshie.crafting.network.PacketSyncCriteria;
import joshie.crafting.network.PacketSyncTriggers;
import joshie.crafting.network.PacketSyncTriggers.SyncPair;
import joshie.crafting.player.PlayerDataServer;
import joshie.crafting.player.nbt.CriteriaNBT;
import joshie.crafting.player.nbt.TriggerNBT;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class CraftingMappings {
    private PlayerDataServer master;
    private UUID uuid;

    protected HashMap<Criteria, Integer> completedCritera = new HashMap(); //All the completed criteria, with a number for how many times repeated
    protected Set<Trigger> completedTriggers = new HashSet(); //All the completed trigger, With their unique name as their identifier, Persistent
    protected HashMap<Trigger, ITriggerData> triggerData = new HashMap(); //Unique String > Data mappings for this trigger

    //Generated by the remapping
    protected Multimap<String, Trigger> activeTriggers; //List of all the active triggers, based on their trigger type

    //Sets the uuid associated with this class
    public void setMaster(PlayerDataServer master) {
        this.master = master;
        this.uuid = master.getUUID();
    }

    public void syncToClient(EntityPlayerMP player) {
        //remap(); //Remap the data, before the client gets sent the data

        PacketHandler.sendToClient(new PacketSyncAbilities(master.getAbilities()), player);
        SyncPair[] values = new SyncPair[CraftAPIRegistry.criteria.size()];
        int pos = 0;
        for (Criteria criteria : CraftAPIRegistry.criteria.values()) {
            int[] numbers = new int[criteria.triggers.size()];
            for (int i = 0; i < criteria.triggers.size(); i++) {
                numbers[i] = i;
            }

            values[pos] = new SyncPair(criteria, numbers);

            pos++;
        }

        PacketHandler.sendToClient(new PacketSyncTriggers(values), player); //Sync all researches to the client
        PacketHandler.sendToClient(new PacketSyncCriteria(true, completedCritera.values().toArray(new Integer[completedCritera.size()]), completedCritera.keySet().toArray(new Criteria[completedCritera.size()])), player); //Sync all conditions to the client
    }

    //Reads the completed criteria
    public void readFromNBT(NBTTagCompound nbt) {
        NBTHelper.readTagCollection(nbt, "Completed Triggers", TriggerNBT.INSTANCE.setCollection(completedTriggers));
        NBTHelper.readMap(nbt, "Completed Criteria", CriteriaNBT.INSTANCE.setMap(completedCritera));
        NBTTagList data = nbt.getTagList("Active Trigger Data", 10);
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound tag = data.getCompoundTagAt(i);
            String name = tag.getString("Name");
            Criteria criteria = CraftAPIRegistry.getCriteriaFromName(name);
            if (criteria != null) {
                for (Trigger trigger : criteria.triggers) {
                    ITriggerData iTriggerData = trigger.getType().newData();
                    iTriggerData.readFromNBT(tag);
                    triggerData.put(trigger, iTriggerData);
                }
            }
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTHelper.writeCollection(nbt, "Completed Triggers", TriggerNBT.INSTANCE.setCollection(completedTriggers));
        NBTHelper.writeMap(nbt, "Completed Criteria", CriteriaNBT.INSTANCE.setMap(completedCritera));
        //Save the extra data for the existing triggers
        NBTTagList data = new NBTTagList();
        for (Trigger trigger : triggerData.keySet()) {
            if (trigger != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Name", trigger.getCriteria().uniqueName);
                ITriggerData iTriggerData = triggerData.get(trigger);
                iTriggerData.writeToNBT(tag);
                data.appendTag(tag);
            }
        }

        nbt.setTag("Active Trigger Data", data);
        return nbt;
    }

    public HashMap<Criteria, Integer> getCompletedCriteria() {
        return completedCritera;
    }

    public Set<Trigger> getCompletedTriggers() {
        return completedTriggers;
    }

    public void markCriteriaAsCompleted(boolean overwrite, Integer[] values, Criteria... conditions) {
        if (overwrite) completedCritera = new HashMap();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) {
                completedCritera.remove(conditions[i]);
            } else completedCritera.put(conditions[i], values[i]);
        }
    }

    public void markTriggerAsCompleted(boolean overwrite, SyncPair[] pairs) {
        if (overwrite) completedTriggers = new HashSet();
        for (SyncPair pair : pairs) {
            if (pair == null || pair.criteria == null) continue; //Avoid broken pairs
            for (int i = 0; i < pair.triggers.length; i++) {
                int num = pair.triggers[i];
                if (pair.criteria.triggers.size() > num) completedTriggers.add(pair.criteria.triggers.get(num));
            }
        }
    }

    private boolean containsAny(List<Criteria> list) {
        for (Criteria criteria : list) {
            if (completedCritera.keySet().contains(criteria)) return true;
        }

        return false;
    }

    private ITriggerData getTriggerData(Trigger trigger) {
        ITriggerData data = triggerData.get(trigger);
        if (data == null) {
            data = trigger.getType().newData();
            triggerData.put(trigger, data);
            return data;
        } else return data;
    }

    /** Called to fire a trigger type, Triggers are only ever called on criteria that is activated **/
    public boolean fireAllTriggers(String type, Object... data) {
        if (activeTriggers == null) return false; //If the remapping hasn't occured yet, say goodbye!
        //If the trigger is a forced completion, then force complete it
        if (type.equals("forced-complete")) {
            Criteria criteria = (Criteria) data[0];
            if (criteria == null || completedCritera.keySet().contains(criteria)) return false; //If null or we completed already return false
            HashSet<Trigger> forRemovalFromActive = new HashSet();
            HashSet<Criteria> toRemap = new HashSet();
            completeCriteria(criteria, forRemovalFromActive, toRemap);
            remapStuff(forRemovalFromActive, toRemap);
            CraftingMod.data.markDirty();
            return true;
        } else if (type.equals("forced-remove")) {
            Criteria criteria = (Criteria) data[0];
            if (criteria == null || !completedCritera.keySet().contains(criteria)) return false;
            else removeCriteria(criteria);
            remap(); //Remap everything
            CraftingMod.data.markDirty();
            return true;
        }

        EntityPlayer player = PlayerHelper.getPlayerFromUUID(uuid);
        World world = player == null ? DimensionManager.getWorld(0) : player.worldObj;
        boolean completedAnyCriteria = false;
        Collection<Trigger> triggers = activeTriggers.get(type);
        HashSet<Trigger> cantContinue = new HashSet();
        List<Trigger> toTrigger = new ArrayList();
        for (Trigger trigger : triggers) {
            Collection<Condition> conditions = trigger.getConditions();
            for (Condition condition : conditions) {
                if (condition.getType().isSatisfied(world, player, uuid) == condition.isInverted()) {
                    cantContinue.add(trigger);
                    break;
                }
            }

            if (cantContinue.contains(trigger)) continue; //Grab the old data
            toTrigger.add(trigger); //Add triggers for firing
        }

        //Fire the trigger
        Collections.shuffle(toTrigger);
        for (Trigger trigger : toTrigger) {
            trigger.getType().onFired(uuid, getTriggerData(trigger), data); //Fire the new data
        }

        //Next step, now that the triggers have been fire, we need to go through them again
        //Check if they have been satisfied, and if so, mark them as completed triggers
        HashSet<Trigger> toRemove = new HashSet();
        for (Trigger trigger : triggers) {
            if (cantContinue.contains(trigger)) continue;
            if (trigger.getType().isCompleted(getTriggerData(trigger))) {
                completedTriggers.add(trigger);
                toRemove.add(trigger);
                PacketHandler.sendToClient(new PacketSyncTriggers(trigger.getCriteria(), trigger.getInternalID()), uuid);
            }
        }

        //Remove completed triggers from the active map
        for (Trigger trigger : toRemove) {
            triggers.remove(toRemove);
        }

        //Create a list of new triggers to add to the active trigger map
        HashSet<Trigger> forRemovalFromActive = new HashSet(); //Reset them
        HashSet<Trigger> forRemovalFromCompleted = new HashSet();
        HashSet<Criteria> toRemap = new HashSet();
        HashSet<Criteria> toComplete = new HashSet();

        //Next step, now that we have fired the trigger, we need to go through all the active criteria
        //We should check if all triggers have been fulfilled
        for (Trigger trigger : triggers) {
            if (cantContinue.contains(trigger) || trigger.getCriteria() == null) continue;
            Criteria criteria = trigger.getCriteria();
            //Check that all triggers are in the completed set
            List<Trigger> allTriggers = criteria.triggers;
            boolean allFired = true;
            for (Trigger criteriaTrigger : allTriggers) { //the completed triggers map, doesn't contains all the requirements, then we need to remove it
                if (!completedTriggers.contains(criteriaTrigger)) allFired = false;
            }

            if (allFired) {
                completedAnyCriteria = true;
                toComplete.add(criteria);
            }
        }

        for (Criteria criteria : toComplete) {
            completeCriteria(criteria, forRemovalFromActive, toRemap);
        }

        remapStuff(forRemovalFromActive, toRemap);
        //Now that we have removed all the triggers, and marked this as completed and remapped data, we should give out the rewards
        for (Criteria criteria : toComplete) {
            for (Reward reward : criteria.rewards   ) {
                reward.getType().reward(uuid);
            }
        }

        //Mark data as dirty, whether it changed or not
        CraftingMod.data.markDirty();
        return completedAnyCriteria;
    }

    public void removeCriteria(Criteria criteria) {
        completedCritera.remove(criteria);
        PacketHandler.sendToClient(new PacketSyncCriteria(false, new Integer[] { 0 }, new Criteria[] { criteria }), uuid);
    }

    private void completeCriteria(Criteria criteria, HashSet<Trigger> forRemovalFromActive, HashSet<Criteria> toRemap) {
        List<Trigger> allTriggers = criteria.triggers;
        int completedTimes = getCriteriaCount(criteria);
        completedTimes++;
        completedCritera.put(criteria, completedTimes);
        //Now that we have updated how times we have completed this quest
        //We should mark all the triggers for removal from activetriggers, as well as actually remove their stored data
        for (Trigger criteriaTrigger : allTriggers) {
            forRemovalFromActive.add(criteriaTrigger);
            //Remove all the conflicts triggers
            for (Criteria conflict : criteria.conflicts) {
                forRemovalFromActive.addAll(conflict.triggers);
            }

            triggerData.remove(criteriaTrigger);
        }

        //The next step in the process is to update the active trigger maps for everything
        //That we unlock with this criteria have been completed
        toRemap.add(criteria);

        if (completedTimes == 1) { //Only do shit if this is the first time it was completed                    
            toRemap.addAll(CraftingRemapper.criteriaToUnlocks.get(criteria));
        }

        PacketHandler.sendToClient(new PacketSyncCriteria(false, new Integer[] { completedTimes }, new Criteria[] { criteria }), uuid);
    }

    private void remapStuff(HashSet<Trigger> forRemovalFromActive, HashSet<Criteria> toRemap) {
        //Removes all the triggers from the active map
        for (Trigger trigger : forRemovalFromActive) {
            activeTriggers.get(trigger.getType().getUnlocalisedName()).remove(trigger);
        }

        //Remap the criteria
        for (Criteria criteria : toRemap) {
            remapCriteriaOnCompletion(criteria);
        }
    }

    public int getCriteriaCount(Criteria criteria) {
        int amount = 0;
        Integer last = completedCritera.get(criteria);
        if (last != null) {
            amount = last;
        }

        return amount;
    }

    private void remapCriteriaOnCompletion(Criteria criteria) {
        Criteria available = null;
        //We are now looping though all criteria, we now need to check to see if this
        //First step is to validate to see if this criteria, is available right now
        //If the criteria is repeatable, or is not completed continue
        int max = criteria.isRepeatable;
        int last = getCriteriaCount(criteria);
        if (last < max) {
            if (completedCritera.keySet().containsAll(criteria.prereqs)) {
                //If we have all the requirements, continue
                //Now that we know that we have all the requirements, we should check for conflicts
                //If it doesn't contain any of the conflicts, continue forwards
                if (!containsAny(criteria.conflicts)) {
                    //The Criteria passed the check for being available, mark it as so
                    available = criteria;
                }
            }

            //If we are allowed to redo triggers, remove from completed
            completedTriggers.removeAll(criteria.triggers);
        }

        if (available != null) {
            List<Trigger> triggers = criteria.triggers; //Grab a list of all the triggers
            for (Trigger trigger : triggers) {
                //If we don't have the trigger in the completed map, mark it as available in the active triggers
                if (!completedTriggers.contains(trigger)) {
                    activeTriggers.get(trigger.getType().getUnlocalisedName()).add((Trigger) trigger);
                }
            }
        }
    }

    public void remap() {
        Set<Criteria> availableCriteria = new HashSet(); //Recreate the available mappings
        activeTriggers = HashMultimap.create(); //Recreate the trigger mappings

        Collection<Criteria> allCriteria = CraftAPIRegistry.criteria.values();
        for (Criteria criteria : allCriteria) {
            //We are now looping though all criteria, we now need to check to see if this
            //First step is to validate to see if this criteria, is available right now
            //If the criteria is repeatable, or is not completed continue
            int max = criteria.isRepeatable;
            int last = getCriteriaCount(criteria);
            if (last < max) {
                if (completedCritera.keySet().containsAll(criteria.prereqs)) {
                    //If we have all the requirements, continue
                    //Now that we know that we have all the requirements, we should check for conflicts
                    //If it doesn't contain any of the conflicts, continue forwards
                    if (!containsAny(criteria.conflicts)) {
                        //The Criteria passed the check for being available, mark it as so
                        availableCriteria.add(criteria);
                    }
                }
            }
        }

        //Now that we have remapped all of the criteria, we should remap the triggers
        for (Criteria criteria : availableCriteria) {
            List<Trigger> triggers = criteria.triggers; //Grab a list of all the triggers
            for (Trigger trigger : triggers) {
                //If we don't have the trigger in the completed map, mark it as available in the active triggers
                if (!completedTriggers.contains(trigger)) {
                    activeTriggers.get(trigger.getType().getUnlocalisedName()).add((Trigger) trigger);
                }
            }
        }
    }
}

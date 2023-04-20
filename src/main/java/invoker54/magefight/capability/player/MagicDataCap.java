package invoker54.magefight.capability.player;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import invoker54.magefight.ArsMageFight;
import invoker54.magefight.config.MageFightConfig;
import invoker54.magefight.network.NetworkHandler;
import invoker54.magefight.network.message.SyncMagicDataMsg;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public class MagicDataCap implements IMagicCap {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String tagNames = "TAG_NAMES";
    private static final String tagCompounds = "TAG_COMPOUNDS";
    private static final String tagSize = "TAG_SIZE";
    protected static final HashMap<LivingEntity, MagicDataCap> caps = new HashMap<>();
    private static final String spellString = "UNLOCKED_BATTLE_SPELLS_STRING";
    private final ArrayList<AbstractSpellPart> unlockedSpells = new ArrayList<>();
    private static final String seenSpellString = "SEEN_SPELLS_STRING";
    private final ArrayList<AbstractSpellPart> seenSpells = new ArrayList<>();
    private static final String tempSpellString = "TEMP_SPELLS_STRING";
    private final ArrayList<AbstractSpellPart> tempSpells = new ArrayList<>();

    private final HashMap<String, CompoundNBT> magicTags = new HashMap<>();

//    public static void refreshCap(Entity mob){
//        LOGGER.debug("THIS IS THE MOB ID I WILL BE REMOVING: " + mob.getId());
//        caps.remove(mob.getId());
//    }

    public static MagicDataCap getCap(LivingEntity entity){
//        return entity.getCapability(MagicDataProvider.CAP_MAGIC_DATA).orElseThrow(NullPointerException::new);
//        LOGGER.debug("THE ID: " + entity.getId());
//        LOGGER.debug("THE NAME: " + entity.getName().getString());
//        LOGGER.debug("IS PRESENT? " + entity.getCapability(MagicDataProvider.CAP_MAGIC_DATA).isPresent());
            if (!caps.containsKey(entity)){
//                LOGGER.info("GRABBING CAP DATA");
                try {
                    MagicDataCap cap = entity.getCapability(MagicDataProvider.CAP_MAGIC_DATA).orElseThrow(NullPointerException::new);
                    if (!cap.isEmpty()) caps.put(entity, cap);
                    return cap;
                }
                catch (Exception e){
//                     LOGGER.error(e);
//                     e.printStackTrace();
                    // LOGGER.info("WHOS THE CULPRIT? " + entity.getName().getString());
                    return CapSaveDelayEvent.grabTempCap(entity);
                }
            }
            return caps.get(entity);
    }

    public boolean isEmpty(){
        return this.magicTags.isEmpty() && this.unlockedSpells.isEmpty();
    }

    public static void syncToClient(LivingEntity entity){
//        LOGGER.info("ATTEMPTING TO SYNC TO CLIENT!");
        if (entity.level.isClientSide){
            // LOGGER.warn("ERROR! TRYING TO SYNC TO CLIENT WHILST ON CLIENT!");
            return;
        }
        //If they are dying or dead and they are NOT a player, there is no reason to sync the data.
        if (!entity.isAlive() && !(entity instanceof PlayerEntity)){
            // LOGGER.debug("WERE THEY REMOVED? " + entity.removed);
            // LOGGER.debug("WERE THEY DEAD? " + (entity.getHealth() <= 0));
            return;
        }
        // LOGGER.debug("ENTITY NAME: " + (entity.getName().getString()));
        // LOGGER.debug("ENTITY ID: " + (entity.getUUID()));
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new SyncMagicDataMsg(getCap(entity).serializeNBT(), entity.getId()));
//        if (entity instanceof PlayerEntity){
//            NetworkHandler.sendToPlayer((PlayerEntity) entity, new SyncMagicDataMsg(getCap(entity).serializeNBT(), entity.getId()));
//        }
    }

    @Override
    public List<AbstractSpellPart> getUnlockedSpells() {
        return this.unlockedSpells;
    }

    public boolean checkSeenSpell(AbstractSpellPart spellPart){
        return this.seenSpells.contains(spellPart);
    }
    public void saveTempSpells(List<AbstractSpellPart> spellPartList){
        this.tempSpells.clear();
        this.tempSpells.addAll(spellPartList);
    }
    public void removeTempSpells(){
        this.tempSpells.clear();
    }
    public List<AbstractSpellPart> getTempSpells(){
        return this.tempSpells;
    }

    @Override
    public void removeSpell(AbstractSpellPart spellPart){
        this.unlockedSpells.remove(spellPart);
    }

    @Override
    public void addSpell(AbstractSpellPart spellPart){
        this.unlockedSpells.add(spellPart);
        if (!this.seenSpells.contains(spellPart)) this.seenSpells.add(spellPart);
    }

    @Override
    public boolean hasTag(String tagName) {
        return (magicTags.containsKey(tagName));
    }

    @Override
    public CompoundNBT getTag(String tagName) {
        if  (magicTags.containsKey(tagName)){
            return magicTags.get(tagName);
        }
//        LOGGER.debug("I AM MAKING A NEW " + tagName + " COMPOUND");
        CompoundNBT newNBT = new CompoundNBT();
        magicTags.put(tagName, newNBT);
        return newNBT;
    }

    @Override
    public void removeTag(String tagName) {
        magicTags.remove(tagName);
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT mainNBT = new CompoundNBT();
        CompoundNBT nameNBT = new CompoundNBT();
        CompoundNBT compoundNBT = new CompoundNBT();

        if (!this.magicTags.isEmpty()) {
//            LOGGER.debug("IT WASN'T EMPTY THE DATA CAP NBT");
            Set<String> names = magicTags.keySet();
            int index = 0;
            //First go through the names
            for (String name : names) {
//                LOGGER.debug("INDEX " + index);
//                LOGGER.debug("NAME " + name);
//                LOGGER.debug("WHATS BEING RETURNED " + magicTags.get(name));
                if (!magicTags.containsKey(name) || getTag(name) == null) continue;
                nameNBT.putString((String.valueOf(index)), name);
                compoundNBT.put((String.valueOf(index)), magicTags.get(name));
                index++;
            }
            //+1 because I started at 0, not 1
            mainNBT.putInt(tagSize, index + 1);
            mainNBT.put(tagNames, nameNBT);
            mainNBT.put(tagCompounds, compoundNBT);
        }

        //All the spells you've unlocked
        String unlockSpells = "";
        for (AbstractSpellPart spellPart: this.unlockedSpells){
            unlockSpells = unlockSpells.concat(spellPart.getTag() + ",");
        }
        mainNBT.putString(spellString, unlockSpells);

        //All the spells you've seen
        String seenSpells = "";
        for (AbstractSpellPart spellPart: this.seenSpells){
            seenSpells = seenSpells.concat(spellPart.getTag() + ",");
        }
        mainNBT.putString(seenSpellString, seenSpells);

        //Your spell choices
        String tempSpells = "";
        for (AbstractSpellPart spellPart: this.tempSpells){
            tempSpells = tempSpells.concat(spellPart.getTag() + ",");
        }
        mainNBT.putString(tempSpellString, tempSpells);

        return mainNBT;
    }

    @Override
    public void deserializeNBT(CompoundNBT mainNBT) {
        this.magicTags.clear();

        CompoundNBT nameNBT = mainNBT.getCompound(tagNames);
        CompoundNBT compoundNBT = mainNBT.getCompound(tagCompounds);
        int size = mainNBT.getInt(tagSize);
        for (int index = 0; index < size; index++) {
            magicTags.put(nameNBT.getString((String.valueOf(index))),
                    (CompoundNBT) compoundNBT.get((String.valueOf(index))));
        }

        //This is for unlocked spells
        this.unlockedSpells.clear();
        String[] stringSpells = mainNBT.getString(spellString).split(",");
        if (stringSpells.length != 0 && !Objects.equals(stringSpells[0], "")) {
            Map<String, AbstractSpellPart> spellMap = ArsNouveauAPI.getInstance().getSpell_map();
            for (String spellPart : stringSpells){
                if (spellMap.containsKey(spellPart)){
                    this.unlockedSpells.add(spellMap.get(spellPart));
                }
            }
        }

        //This is for all the spells you've seen
        this.seenSpells.clear();
        stringSpells = mainNBT.getString(seenSpellString).split(",");
        if (stringSpells.length != 0 && !Objects.equals(stringSpells[0], "")) {
            Map<String, AbstractSpellPart> spellMap = ArsNouveauAPI.getInstance().getSpell_map();
            for (String spellPart : stringSpells){
                if (spellMap.containsKey(spellPart)){
                    this.seenSpells.add(spellMap.get(spellPart));
                }
            }
        }

        //This is for all the spell choices you have
        this.tempSpells.clear();
        stringSpells = mainNBT.getString(tempSpellString).split(",");
        if (stringSpells.length != 0 && !Objects.equals(stringSpells[0], "")) {
            Map<String, AbstractSpellPart> spellMap = ArsNouveauAPI.getInstance().getSpell_map();
            for (String spellPart : stringSpells){
                if (spellMap.containsKey(spellPart)){
                    this.tempSpells.add(spellMap.get(spellPart));
                }
            }
        }
    }

    public static class MagicDataStorage implements Capability.IStorage<MagicDataCap>{

        @Nullable
        @Override
        public INBT writeNBT(Capability<MagicDataCap> capability, MagicDataCap instance, Direction side) {
            return instance.serializeNBT();
        }

        @Override
        public void readNBT(Capability<MagicDataCap> capability, MagicDataCap instance, Direction side, INBT nbt) {
            CompoundNBT mainNbt = (CompoundNBT) nbt;
            instance.deserializeNBT(mainNbt);
        }
    }

    @Mod.EventBusSubscriber(modid = ArsMageFight.MOD_ID)
    public static class CapEvents {

        @SubscribeEvent
        public static void attachCap(AttachCapabilitiesEvent<Entity> event){
            if (event.getObject() instanceof LivingEntity){
                event.addCapability(MagicDataProvider.CAP_MAGIC_DATA_LOC, new MagicDataProvider());
            }
        }

        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event){
            if (!event.isWasDeath()) return;
            if (event.isCanceled()) return;
            // LOGGER.debug("IS THIS CLONE EVENT CLIENT SIDE? " + event.getEntityLiving().level.isClientSide);
            // LOGGER.debug("WHATS THE NEW ID: " + event.getPlayer().getId());
            // LOGGER.debug("WHATS THE OLD ID: " + event.getOriginal().getId());

            MagicDataCap newCap = MagicDataCap.getCap(event.getPlayer());

            //The new player id will be reverted to the old player id, so just mess with the old cap instead.
            MagicDataCap oldCap = MagicDataCap.getCap(event.getOriginal());
            List<AbstractSpellPart> spellList = oldCap.getUnlockedSpells();
            for (int a = 0; a < MageFightConfig.deathGlyphLoss; a++){
                if (spellList.isEmpty()) break;

                //Remove glyphs on death
                oldCap.removeSpell(spellList.get(event.getPlayer().getRandom().nextInt(spellList.size())));
            }
            newCap.deserializeNBT(oldCap.serializeNBT());

            //Replace the old saved magic cap with the new magic cap
            caps.clear();
//            LOGGER.debug("WAS THE CAP REPLACEMENT SUCCESSFUL? " + (caps.replace(event.getOriginal(), oldCap, newCap)));
//            caps.remove(event.getPlayer());

//            //Sync the old ID since the new one ends up reverting to the old one anyways.
//            MagicDataCap.syncToClient(event.getOriginal());
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event){
            if (event.isEndConquered()) return;
            MagicDataCap.syncToClient(event.getPlayer());
        }

        @SubscribeEvent
        public static void onClose (WorldEvent.Unload event){
            if (event.getWorld().isClientSide()) return;

            //Clear the cached caps
            caps.clear();
            // LOGGER.info("I AM CLEARING ALL OF THE SAVED CAP DATA!!");
            // LOGGER.info("WHATS THE CAP SIZE?: " + caps.size());
        }

        @SubscribeEvent
        public static void onPlayerJoinServer(PlayerEvent.PlayerLoggedInEvent event){
            MagicDataCap.syncToClient(event.getPlayer());
        }

        @SubscribeEvent
        public static void onEntityLeave(EntityLeaveWorldEvent event){
            if (!caps.containsKey(event.getEntity().getId())) return;
//            LOGGER.debug("ENTITY LEAVING: " + event.getEntity().getName().getString());

            caps.remove(event.getEntity().getId());
        }
    }
}

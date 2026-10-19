package packets.incoming;

import packets.Packet;
import packets.reader.BufferReader;

import java.util.Arrays;

/**
 * Received when the player enters or updates their vault
 */
public class VaultContentPacket extends Packet {
    /**
     * If this is the last vault packet
     */
    public boolean lastVaultPacket;
    /**
     * Vault chest object ID
     */
    public int vaultChestObjectId;
    /**
     * material  ID
     */
    public int materialChestObjectId;
    /**
     * Gift chest object ID
     */
    public int giftChestObjectId;
    /**
     * Potion storage object ID
     */
    public int potionStorageObjectId;
    /**
     * Seasonal spoils object ID
     */
    public int seasonalSpoilChestObjectId;
    /**
     * The contents of the players vault, sent as an array of item object IDs or -1 if the slot is empty
     */
    public int[] vaultContents;
    /**
     * The material contents of the player
     */
    int[] materialContents;
    /**
     * The contents of the player's gift vault
     */
    public int[] giftContents;
    /**
     * The contents of the player's potion vault
     */
    public int[] potionContents;
    /**
     * The contents of the player's seasonal spoils items
     */
    int[] seasonalSpoilContent;
    /**
     * The cost in gold for the next upgrade to the vault
     */
    public short vaultUpgradeCost;
    /**
     * The cost in gold for the next upgrade to the potion vault
     */
    public short potionUpgradeCost;
    /**
     * The current slot size of the player's potion vault
     */
    public short currentPotionMax;
    /**
     * The size of the player's potion vault after they purchase the current upgrade
     */
    public short nextPotionMax;
    /**
     * String indicating enchantments in player's vault
     */
    public String vaultChestEnchants;
    /**
     * String indicating enchantments in player's gift vault
     */
    public String giftChestEnchants;
    /**
     * String indicating enchantments in player's spoils chest vault
     */
    public String spoilsChestEnchants;
    /**
     * Cost to upgrade materials
     */
    public short materialUpgradeCost;

    /**
     * Forgiving version (White Bag Pup): reads the containers first and stops quietly if the game has
     * changed the rest of the message, instead of throwing the whole vault away.
     */
    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        vaultContents = new int[0];
        materialContents = new int[0];
        giftContents = new int[0];
        potionContents = new int[0];
        seasonalSpoilContent = new int[0];
        vaultChestEnchants = giftChestEnchants = spoilsChestEnchants = "";
        String stopped = null;
        try {
            lastVaultPacket = buffer.readBoolean();
            vaultChestObjectId = buffer.readCompressedInt();
            materialChestObjectId = buffer.readCompressedInt();
            giftChestObjectId = buffer.readCompressedInt();
            potionStorageObjectId = buffer.readCompressedInt();
            seasonalSpoilChestObjectId = buffer.readCompressedInt();
            vaultContents = readList(buffer);
            materialContents = readList(buffer);
            giftContents = readList(buffer);
            potionContents = readList(buffer);
            seasonalSpoilContent = readList(buffer);
        } catch (Exception e) {
            stopped = "containers";
        }
        if (stopped == null) {
            try {
                vaultUpgradeCost = buffer.readShort();
                materialUpgradeCost = buffer.readShort();
                potionUpgradeCost = buffer.readShort();
                currentPotionMax = buffer.readShort();
                nextPotionMax = buffer.readShort();
                vaultChestEnchants = buffer.readString();
                giftChestEnchants = buffer.readString();
                spoilsChestEnchants = buffer.readString();
            } catch (Exception e) {
                stopped = "extras";
            }
        }
        int left = buffer.getRemainingBytes();
        System.out.println("[White Bag Pup] vault message: vault " + vaultContents.length + ", potions " + potionContents.length
                + ", gifts " + giftContents.length + ", last piece " + lastVaultPacket
                + (stopped != null ? ", stopped reading at the " + stopped + " (" + buffer.getIndex() + "/" + buffer.size() + " bytes)"
                : left > 0 ? ", " + left + " extra bytes ignored" : ""));
        if (left > 0) buffer.readBytes(left); // skip anything new the game added
    }

    private static int[] readList(BufferReader buffer) {
        int n = buffer.readCompressedInt();
        if (n < 0 || n > buffer.getRemainingBytes()) throw new IllegalStateException("bad list length " + n);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = buffer.readCompressedInt();
        return out;
    }

    @Override
    public String toString() {
        return "VaultContentPacket{" +
                "\n   lastVaultPacket=" + lastVaultPacket +
                "\n   vaultChestObjectId=" + vaultChestObjectId +
                "\n   materialChestObjectId=" + materialChestObjectId +
                "\n   giftChestObjectId=" + giftChestObjectId +
                "\n   potionStorageObjectId=" + potionStorageObjectId +
                "\n   seasonalSpoilChestObjectId=" + seasonalSpoilChestObjectId +
                "\n   vaultContents=" + Arrays.toString(vaultContents) +
                "\n   materialContents=" + Arrays.toString(materialContents) +
                "\n   giftContents=" + Arrays.toString(giftContents) +
                "\n   potionContents=" + Arrays.toString(potionContents) +
                "\n   seasonalSpoilContent=" + Arrays.toString(seasonalSpoilContent) +
                "\n   vaultUpgradeCost=" + vaultUpgradeCost +
                "\n   potionUpgradeCost=" + potionUpgradeCost +
                "\n   currentPotionMax=" + currentPotionMax +
                "\n   nextPotionMax=" + nextPotionMax +
                "\n   vaultChestEnchants=" + vaultChestEnchants +
                "\n   giftChestEnchants=" + giftChestEnchants +
                "\n   spoilsChestEnchants=" + spoilsChestEnchants +
                "\n   materialUpgradeCost=" + materialUpgradeCost;
    }
}
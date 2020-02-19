/*
 * Copyright 2019 ProtectionStones team and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package dev.espi.protectionstones;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.utils.MiscUtil;
import dev.espi.protectionstones.utils.WGUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Wrapper for a Bukkit player so that it exposes ProtectionStones related methods.
 */

public class PSPlayer {

    @Getter
    @Setter
    UUID uuid;

    Player p;

    /**
     * Adapt a UUID into a PSPlayer wrapper.
     *
     * @param uuid the uuid to wrap
     * @return the PSPlayer object
     */

    public static PSPlayer fromUUID(@NonNull UUID uuid) {
        return new PSPlayer(uuid);
    }

    /**
     * Adapt a Bukkit player into a PSPlayer wrapper.
     *
     * @param p the player to wrap
     * @return the PSPlayer object
     */

    public static PSPlayer fromPlayer(@NonNull Player p) {
        return new PSPlayer(p);
    }

    public static PSPlayer fromPlayer(@NonNull OfflinePlayer p) {
        return new PSPlayer((Player) p);
    }

    public PSPlayer(Player player) {
        this.p = player;
        this.uuid = player.getUniqueId();
    }

    public PSPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * Get the wrapped Bukkit player.
     * It may return null if the object wraps a UUID that does not exist.
     *
     * @return the player, or null
     */

    public Player getPlayer() {
        if (p == null) return Bukkit.getPlayer(uuid);
        return p;
    }

    /**
     * Get the wrapped Bukkit offline player.
     * Safer to use than getPlayer (this does not return a null)
     * It may return an empty player if the object wraps a UUID that does not exist.
     *
     * @return the offline player
     */

    public OfflinePlayer getOfflinePlayer() {
        if (p == null) return Bukkit.getOfflinePlayer(uuid);
        return p;
    }

    public String getName() {
        return getPlayer().getName();
    }

    /**
     * Get if the player has a certain amount of money.
     * Vault must be enabled!
     *
     * @param amount the amount to check
     * @return whether the player has this amount of money
     */

    public boolean hasAmount(double amount) {
        if (!ProtectionStones.getInstance().isVaultSupportEnabled()) return false;
        return ProtectionStones.getInstance().getVaultEconomy().has(getOfflinePlayer(), amount);
    }

    /**
     * Get the player's balance.
     * Vault must be enabled!
     *
     * @return the amount of money the player has
     */

    public double getBalance() {
        if (!ProtectionStones.getInstance().isVaultSupportEnabled()) return 0;
        return ProtectionStones.getInstance().getVaultEconomy().getBalance(getOfflinePlayer());
    }

    /**
     * Add a certain amount to the player's bank account.
     * Vault must be enabled! Must be run on main thread!
     *
     * @param amount the amount to add
     * @return the {@link EconomyResponse} that is given by Vault
     */

    public EconomyResponse depositBalance(double amount) {
        if (ProtectionStones.getInstance().getVaultEconomy() == null) return null;
        return ProtectionStones.getInstance().getVaultEconomy().depositPlayer(getOfflinePlayer(), amount);
    }

    /**
     * Withdraw a certain amount from the player's bank account.
     * Vault must be enabled! Must be run on main thread!
     *
     * @param amount the amount to withdraw
     * @return the {@link EconomyResponse} that is given by Vault
     */

    public EconomyResponse withdrawBalance(double amount) {
        if (ProtectionStones.getInstance().getVaultEconomy() == null) return null;
        return ProtectionStones.getInstance().getVaultEconomy().withdrawPlayer(getOfflinePlayer(), amount);
    }

    /**
     * Pay another player a certain amount of money
     * Vault must be enabled! Must be run on main thread!
     *
     * @param payee  the player to pay
     * @param amount the amount to pay
     */

    public void pay(PSPlayer payee, double amount) {
        withdrawBalance(amount);
        payee.depositBalance(amount);
    }

    /**
     * Get a player's permission limits for each protection block (protectionstones.limit.alias.x)
     * Protection blocks that aren't specified in the player's permissions will not be returned in the map.
     *
     * @return a hashmap containing a psprotectblock object to an integer, which is the number of protection regions of that type the player is allowed to place
     */

    public HashMap<PSProtectBlock, Integer> getRegionLimits() {
        HashMap<PSProtectBlock, Integer> regionLimits = new HashMap<>();
        for (PermissionAttachmentInfo rawperm : getPlayer().getEffectivePermissions()) {
            String perm = rawperm.getPermission();

            if (perm.startsWith("protectionstones.limit")) {
                String[] spl = perm.split("\\.");

                if (spl.length == 4 && ProtectionStones.getProtectBlockFromAlias(spl[2]) != null) {
                    PSProtectBlock block = ProtectionStones.getProtectBlockFromAlias(spl[2]);
                    int limit = Integer.parseInt(spl[3]);
                    if (regionLimits.get(block) == null || regionLimits.get(block) < limit) { // only use max limit
                        regionLimits.put(block, limit);
                    }
                }
            }
        }
        return regionLimits;
    }

    /**
     * Get a player's total protection limit from permission (protectionstones.limit.x)
     *
     * @return the number of protection regions the player can have, or -1 if there is no limit set.
     */

    public int getGlobalRegionLimits() {
        return MiscUtil.getPermissionNumber(getPlayer(), "protectionstones.limit.", -1);
    }

    /**
     * Get the list of regions that a player owns, or is a member of. It is recommended to run this asynchronously
     * since the query can be slow.
     *
     * @param w           world to search for regions in
     * @param canBeMember whether or not to add regions where the player is a member, not owner
     * @return list of regions that the player owns (or is a part of if canBeMember is true)
     */

    public List<PSRegion> getPSRegions(World w, boolean canBeMember) {
        RegionManager rgm = WGUtils.getRegionManagerWithWorld(w);
        if (rgm == null) return new ArrayList<>();

        List<PSRegion> regions = new ArrayList<>();
        for (ProtectedRegion r : rgm.getRegions().values()) {
            if (ProtectionStones.isPSRegion(r) && (r.getOwners().contains(uuid) || (canBeMember && r.getMembers().contains(uuid)))) {
                regions.add(PSRegion.fromWGRegion(w, r));
            }
        }
        return regions;
    }

}
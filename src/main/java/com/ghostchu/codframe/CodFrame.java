package com.ghostchu.codframe;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public final class CodFrame extends JavaPlugin implements Listener {
    private final NamespacedKey KEY = new NamespacedKey(this, "owner");
    private final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public Optional<UUID> queryProtection(ItemFrame frame) {
        String dat = frame.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (dat == null) {
            return Optional.empty();
        }
        UUID uuid = UUID.fromString(dat);
        return Optional.of(uuid);
    }

    public boolean claimProtection(ItemFrame frame, UUID uuid) {
        // Whether the protection is exists or not, add basic protection first to avoid bug
        frame.setFixed(true);
        frame.setInvulnerable(true);
        if (queryProtection(frame).isPresent()) { // already has an owner
            return false;
        }
        // add owner key for this player
        frame.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, uuid.toString());
        return true;
    }

    public boolean removeProtection(ItemFrame frame, UUID uuid) {
        if (!queryProtection(frame).isPresent()) {
            return false;
        }
        frame.setFixed(false);
        frame.getPersistentDataContainer().remove(KEY);
        frame.setInvulnerable(false);
        return true;
    }

    public void openBook(ItemFrame frame, Player player) {
        if (frame.getItem().getItemMeta() instanceof BookMeta meta) {
            Component author = meta.author();
            Component title = meta.title();
            if (author == null) {
                author = MINI_MESSAGE.deserialize(getConfig().getString("messages.book.anonymous", ""));
            }
            if (title == null) {
                title = MINI_MESSAGE.deserialize(getConfig().getString("messages.book.no-title", ""));
            }
            player.openBook(Book.book(title, author, meta.pages()));
        }
    }

    public void playerDoProtection(Player player, ItemFrame frame) {
        if (claimProtection(frame, player.getUniqueId())) { // success add
            player.sendMessage(MINI_MESSAGE.deserialize(getConfig().getString("messages.frame.success-set", "")));
        } else { // already has an owner key
            Optional<UUID> uuid = queryProtection(frame);
            if (uuid.get().equals(player.getUniqueId()) || player.hasPermission("codframe.admin")) {
                removeProtection(frame, player.getUniqueId());
                player.sendMessage(MINI_MESSAGE.deserialize(getConfig().getString("messages.frame.success-remove", "")));
            } else {
                String message = getConfig()
                        .getString("messages.frame.failed-other-protected", "")
                        .replace("<0>", getPlayerName(uuid));
                player.sendMessage(MINI_MESSAGE.deserialize(message));
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            Entity target = player.getTargetEntity(4);
            if (target instanceof ItemFrame frame) {
                playerDoProtection(player, frame);
            } else {
                player.sendMessage(MINI_MESSAGE.deserialize(getConfig().getString("messages.frame.failed-invalid", "")));
            }
            return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDropping(HangingBreakEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (queryProtection(frame).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDamaging(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (queryProtection(frame).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDamaging(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (queryProtection(frame).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDamaging(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (queryProtection(frame).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void interactFrame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame frame) {
            Optional<UUID> uuid = queryProtection(frame);
            if (event.getPlayer().isSneaking()) { // Sneak Toggle Protection
                event.setCancelled(true);
                playerDoProtection(event.getPlayer(), frame);
            } else if (uuid.isPresent()) { // Preview normally
                event.setCancelled(true);
                event.getPlayer().sendActionBar(
                        MINI_MESSAGE.deserialize(getConfig()
                                .getString("messages.general.owner-info", "")
                                .replace("<0>", getPlayerName(uuid))
                        )
                );
                if (frame.getItem().hasItemMeta()) {
                    openBook(frame, event.getPlayer());
                    sendChatPreview(frame.getItem(), event.getPlayer());
                }
            }
        }
    }

    private void sendChatPreview(ItemStack item, Player player) {
        player.sendMessage(MINI_MESSAGE.deserialize(getConfig().getString("messages.general.hover-preview", "")).hoverEvent(item.asHoverEvent()));
    }

    private @NotNull String getPlayerName(Optional<UUID> uuid) {
        // try to replace with owner username
        Player owner = Bukkit.getPlayer(uuid.get());
        if (owner != null) {
            return owner.getName();
        } else {
            OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(uuid.get());
            if (offlineOwner.getName() != null) {
                return offlineOwner.getName();
            } else {
                return "???";
            }
        }
    }
}

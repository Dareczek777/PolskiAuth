package pl.polski.auth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PolskiAuth extends JavaPlugin implements Listener {
    private final Set<UUID> logged = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BossBar> loginBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> loginTasks = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PolskiAuth został uruchomiony.");
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', (text == null ? "" : text).replace("\\n", "\n"));
    }
    private String text(String path) { return color(getConfig().getString(path, "&cBrak tekstu: " + path)); }
    private String format(String path, String... replacements) {
        String result = text(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) result = result.replace("{" + replacements[i] + "}", replacements[i + 1]);
        return result;
    }
    private void msg(Player p, String path) { p.sendMessage(text("messages.prefix") + text(path)); }

    private boolean registered(UUID id) { return getConfig().contains("accounts." + id); }
    private String hash(String password, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), 120000, 256);
        byte[] result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return Base64.getEncoder().encodeToString(result);
    }
    private String newSalt() {
        byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    private boolean passwordMatches(UUID id, String password) {
        try {
            String path = "accounts." + id;
            return hash(password, getConfig().getString(path + ".salt")).equals(getConfig().getString(path + ".hash"));
        } catch (Exception ex) { getLogger().warning("Nie udało się sprawdzić hasła: " + ex.getMessage()); return false; }
    }
    private void savePassword(UUID id, String password) throws Exception {
        String salt = newSalt(), path = "accounts." + id;
        getConfig().set(path + ".salt", salt);
        getConfig().set(path + ".hash", hash(password, salt));
        getConfig().set(path + ".name", Bukkit.getOfflinePlayer(id).getName());
        saveConfig();
    }

    private void startLoginTimer(Player p) {
        UUID id = p.getUniqueId();
        int timeout = Math.max(10, getConfig().getInt("settings.login-timeout", 60));
        BossBar bar = Bukkit.createBossBar(format("bossbar.text", "seconds", String.valueOf(timeout)), BarColor.YELLOW, BarStyle.SOLID);
        bar.addPlayer(p);
        bar.setProgress(1.0);
        loginBars.put(id, bar);
        final int[] remaining = {timeout};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!p.isOnline() || logged.contains(id)) { stopLoginTimer(id); return; }
            remaining[0]--;
            bar.setProgress(Math.max(0.0, remaining[0] / (double) timeout));
            bar.setTitle(format("bossbar.text", "seconds", String.valueOf(remaining[0])));
            if (remaining[0] <= 10 && remaining[0] > 0) {
                p.sendTitle(text("titles.warning"), format("titles.warning-subtitle", "seconds", String.valueOf(remaining[0])), 0, 20, 0);
            }
            if (remaining[0] <= 0) {
                stopLoginTimer(id);
                p.kickPlayer(text("messages.kick-timeout"));
            }
        }, 20L, 20L);
        loginTasks.put(id, task);
    }
    private void stopLoginTimer(UUID id) {
        BukkitTask task = loginTasks.remove(id);
        if (task != null) task.cancel();
        BossBar bar = loginBars.remove(id);
        if (bar != null) bar.removeAll();
    }
    private void success(Player p) {
        logged.add(p.getUniqueId());
        stopLoginTimer(p.getUniqueId());
        msg(p, "messages.login-success");
        p.sendTitle(text("titles.success"), text("titles.success-subtitle"), 5, 35, 10);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        logged.remove(p.getUniqueId());
        if (registered(p.getUniqueId())) {
            msg(p, "messages.welcome-registered");
            p.sendTitle(text("titles.registered"), text("titles.registered-subtitle"), 10, 80, 10);
        } else {
            msg(p, "messages.welcome-new");
            p.sendTitle(text("titles.unregistered"), text("titles.unregistered-subtitle"), 10, 100, 10);
        }
        startLoginTimer(p);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        logged.remove(id);
        stopLoginTimer(id);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(text("messages.player-only")); return true; }
        UUID id = p.getUniqueId();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        int min = getConfig().getInt("settings.min-password-length", 5);
        int max = getConfig().getInt("settings.max-password-length", 64);
        if (cmd.equals("register")) {
            if (registered(id)) { msg(p, "messages.already-registered"); return true; }
            if (args.length != 2) { msg(p, "messages.register-usage"); return true; }
            if (!args[0].equals(args[1])) { msg(p, "messages.passwords-different"); return true; }
            if (args[0].length() < min || args[0].length() > max) { p.sendMessage(text("messages.prefix") + format("messages.password-length", "min", String.valueOf(min), "max", String.valueOf(max))); return true; }
            try { savePassword(id, args[0]); success(p); }
            catch (Exception ex) { msg(p, "messages.register-error"); getLogger().severe(ex.getMessage()); }
            return true;
        }
        if (cmd.equals("login")) {
            if (!registered(id)) { msg(p, "messages.not-registered"); return true; }
            if (logged.contains(id)) { msg(p, "messages.already-logged"); return true; }
            if (args.length != 1) { msg(p, "messages.login-usage"); return true; }
            if (!passwordMatches(id, args[0])) { msg(p, "messages.wrong-password"); return true; }
            success(p); return true;
        }
        if (cmd.equals("changepassword")) {
            if (!logged.contains(id)) { msg(p, "messages.must-login"); return true; }
            if (args.length != 2) { msg(p, "messages.change-usage"); return true; }
            if (!passwordMatches(id, args[0])) { msg(p, "messages.old-password-wrong"); return true; }
            if (args[1].length() < min || args[1].length() > max) { p.sendMessage(text("messages.prefix") + format("messages.password-length", "min", String.valueOf(min), "max", String.valueOf(max))); return true; }
            try { savePassword(id, args[1]); msg(p, "messages.password-changed"); }
            catch (Exception ex) { msg(p, "messages.change-error"); }
            return true;
        }
        return false;
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (!logged.contains(e.getPlayer().getUniqueId()) && e.getTo() != null &&
            (e.getFrom().getBlockX()!=e.getTo().getBlockX() || e.getFrom().getBlockY()!=e.getTo().getBlockY() || e.getFrom().getBlockZ()!=e.getTo().getBlockZ())) e.setTo(e.getFrom());
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        if (!logged.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); e.getPlayer().sendMessage(text("messages.prefix") + text("messages.must-login-chat")); }
    }
    @EventHandler public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        if (logged.contains(e.getPlayer().getUniqueId())) return;
        String root = e.getMessage().split(" ")[0].substring(1).toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        if (!Set.of("register","reg","zarejestruj","login","l","zaloguj").contains(root)) {
            e.setCancelled(true); msg(e.getPlayer(), "messages.commands-blocked");
        }
    }
    @EventHandler public void onInteract(PlayerInteractEvent e) { if (!logged.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent e) { if (!logged.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (!logged.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onInventory(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && !logged.contains(p.getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && !logged.contains(p.getUniqueId())) e.setCancelled(true); }
}

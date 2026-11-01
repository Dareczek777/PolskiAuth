package pl.polski.auth;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PolskiAuth extends JavaPlugin implements Listener {
    private final Set<UUID> logged = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> joinLocations = new HashMap<>();
    private final Map<UUID, BossBar> loginBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> loginTasks = new ConcurrentHashMap<>();
    private static final int LOGIN_TIMEOUT_SECONDS = 60;
    private static final String PREFIX = "§8[§6Anarchia§8] §7";

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PolskiAuth został uruchomiony.");
    }

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
            return hash(password, getConfig().getString(path + ".salt")) .equals(getConfig().getString(path + ".hash"));
        } catch (Exception ex) { getLogger().warning("Nie udało się sprawdzić hasła: " + ex.getMessage()); return false; }
    }
    private void savePassword(UUID id, String password) throws Exception {
        String salt = newSalt(), path = "accounts." + id;
        getConfig().set(path + ".salt", salt);
        getConfig().set(path + ".hash", hash(password, salt));
        getConfig().set(path + ".name", getServer().getOfflinePlayer(id).getName());
        saveConfig();
    }
    private void msg(Player p, String text) { p.sendMessage(PREFIX + text); }
    private void startLoginTimer(Player p) {
        UUID id = p.getUniqueId();
        BossBar bar = Bukkit.createBossBar("§6Czas na zalogowanie: §e" + LOGIN_TIMEOUT_SECONDS + "s", BarColor.YELLOW, BarStyle.SOLID);
        bar.addPlayer(p);
        bar.setProgress(1.0);
        loginBars.put(id, bar);

        final int[] remaining = {LOGIN_TIMEOUT_SECONDS};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!p.isOnline() || logged.contains(id)) {
                stopLoginTimer(id);
                return;
            }
            remaining[0]--;
            double progress = Math.max(0.0, remaining[0] / (double) LOGIN_TIMEOUT_SECONDS);
            bar.setProgress(progress);
            bar.setTitle("§6Czas na zalogowanie: §e" + remaining[0] + "s");
            if (remaining[0] <= 10 && remaining[0] > 0) {
                p.sendTitle("§cZALOGUJ SIĘ!", "§7Pozostało §e" + remaining[0] + " sekund", 0, 20, 0);
            }
            if (remaining[0] <= 0) {
                stopLoginTimer(id);
                p.kickPlayer("§cNie zalogowałeś się w wyznaczonym czasie!\n§7Połącz się ponownie i zaloguj.");
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
        joinLocations.remove(p.getUniqueId());
        msg(p, "§aZostałeś pomyślnie zalogowany!");
        p.sendTitle("§aZALOGOWANO", "§7Miłej gry!", 5, 35, 10);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        logged.remove(p.getUniqueId());
        joinLocations.put(p.getUniqueId(), p.getLocation().clone());
        if (registered(p.getUniqueId())) {
            msg(p, "§eWitaj ponownie! §7Zaloguj się komendą §a/l <hasło>");
            p.sendTitle("§6§lANARCHIA", "§eZaloguj się: §a/l <hasło>", 10, 80, 10);
        } else {
            msg(p, "§eWitaj! §7Zarejestruj konto: §a/reg <hasło> <powtórz hasło>");
            p.sendTitle("§6§lANARCHIA", "§eZarejestruj się: §a/reg <hasło> <powtórz>", 10, 100, 10);
        }
        startLoginTimer(p);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        logged.remove(id);
        joinLocations.remove(id);
        stopLoginTimer(id);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Ta komenda jest tylko dla gracza."); return true; }
        UUID id = p.getUniqueId();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("register")) {
            if (registered(id)) { msg(p, "§cMasz już konto. Użyj §e/l <hasło>§c."); return true; }
            if (args.length != 2) { msg(p, "§cUżycie: §e/reg <hasło> <powtórz hasło>"); return true; }
            if (!args[0].equals(args[1])) { msg(p, "§cPodane hasła nie są takie same."); return true; }
            if (args[0].length() < 5) { msg(p, "§cHasło musi mieć co najmniej 5 znaków."); return true; }
            if (args[0].length() > 64) { msg(p, "§cHasło może mieć maksymalnie 64 znaki."); return true; }
            try { savePassword(id, args[0]); success(p); }
            catch (Exception ex) { msg(p, "§cWystąpił błąd podczas rejestracji. Spróbuj ponownie."); getLogger().severe(ex.getMessage()); }
            return true;
        }
        if (cmd.equals("login")) {
            if (!registered(id)) { msg(p, "§cNie masz konta. Użyj §e/reg <hasło> <powtórz hasło>§c."); return true; }
            if (logged.contains(id)) { msg(p, "§aJesteś już zalogowany."); return true; }
            if (args.length != 1) { msg(p, "§cUżycie: §e/l <hasło>"); return true; }
            if (!passwordMatches(id, args[0])) { msg(p, "§cNieprawidłowe hasło!"); return true; }
            success(p); return true;
        }
        if (cmd.equals("changepassword")) {
            if (!logged.contains(id)) { msg(p, "§cNajpierw musisz się zalogować."); return true; }
            if (args.length != 2) { msg(p, "§cUżycie: §e/zmienhaslo <stare> <nowe>"); return true; }
            if (!passwordMatches(id, args[0])) { msg(p, "§cStare hasło jest nieprawidłowe."); return true; }
            if (args[1].length() < 5 || args[1].length() > 64) { msg(p, "§cNowe hasło musi mieć od 5 do 64 znaków."); return true; }
            try { savePassword(id, args[1]); msg(p, "§aHasło zostało zmienione."); }
            catch (Exception ex) { msg(p, "§cNie udało się zmienić hasła."); }
            return true;
        }
        return false;
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (!logged.contains(e.getPlayer().getUniqueId()) && e.getTo() != null &&
            (e.getFrom().getBlockX()!=e.getTo().getBlockX() || e.getFrom().getBlockY()!=e.getTo().getBlockY() || e.getFrom().getBlockZ()!=e.getTo().getBlockZ())) {
            e.setTo(e.getFrom());
        }
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        if (!logged.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); e.getPlayer().sendMessage(PREFIX + "§cNajpierw się zaloguj lub zarejestruj."); }
    }
    @EventHandler public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        if (logged.contains(e.getPlayer().getUniqueId())) return;
        String root = e.getMessage().split(" ")[0].substring(1).toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        if (!Set.of("register","reg","zarejestruj","login","l","zaloguj").contains(root)) {
            e.setCancelled(true); msg(e.getPlayer(), "§cPrzed zalogowaniem możesz używać tylko §e/reg §ci §e/l§c.");
        }
    }
    @EventHandler public void onInteract(PlayerInteractEvent e) { if (!logged.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent e) { if (!logged.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (!logged.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onInventory(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && !logged.contains(p.getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && !logged.contains(p.getUniqueId())) e.setCancelled(true); }
}

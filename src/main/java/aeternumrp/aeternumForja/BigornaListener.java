
package aeternumrp.aeternumForja;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BigornaListener implements Listener {

    private final JavaPlugin plugin;
    private final File arquivoForjas;
    private final YamlConfiguration dados;
    private final ForjaMinigame minigame;

    private final Map<Location, ItemDisplay> bigornasOcupadas =
            new HashMap<>();

    public BigornaListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.minigame = new ForjaMinigame(plugin);
        this.arquivoForjas = new File(plugin.getDataFolder(), "forjas.yml");
        this.dados = YamlConfiguration.loadConfiguration(arquivoForjas);

        carregarForjas();
    }


    @EventHandler
    public void onAnvilClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material type = block.getType();
        if (type != Material.ANVIL
                && type != Material.CHIPPED_ANVIL
                && type != Material.DAMAGED_ANVIL) {
            return;
        }

        Player player = event.getPlayer();
        Location key = block.getLocation();

        if (!player.hasPermission("aeternumforja.usar")) {
            event.setCancelled(true);
            player.sendMessage(
                    "§cVocê precisa ser um ferreiro para usar esta forja!"
            );
            return;
        }

        // Botão esquerdo: inicia o minigame.
        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);

            ItemDisplay existente = bigornasOcupadas.get(key);

            if (existente == null || !existente.isValid()) {
                player.sendMessage(
                        "§cNão há item nesta bigorna para forjar."
                );
                return;
            }

            minigame.iniciar(player);
            return;
        }

        // Botão direito: mantém o comportamento atual.
        event.setCancelled(true);

        ItemStack item = event.getItem();

        if (item == null || item.getType().isAir()) {
            retirarItemDaForja(player, key);
            return;
        }

        ItemDisplay existente = bigornasOcupadas.get(key);
        if (existente != null && existente.isValid()) {
            player.sendMessage("§cEsta bigorna já está ocupada!");
            return;
        }

        bigornasOcupadas.remove(key);

        ItemStack itemForja = item.clone();
        itemForja.setAmount(1);

        Location localDisplay = key.clone().add(0.5, 1.05, 0.5);

        ItemDisplay display = block.getWorld().spawn(
                localDisplay,
                ItemDisplay.class,
                entidade -> {
                    entidade.setItemStack(itemForja);
                    entidade.setItemDisplayTransform(
                            ItemDisplayTransform.FIXED
                    );
                    entidade.setTransformation(
                            new Transformation(
                                    new Vector3f(0, 0, 0),
                                    new Quaternionf().rotateX(
                                            (float) Math.toRadians(270)
                                    ),
                                    new Vector3f(0.65f, 0.65f, 0.65f),
                                    new Quaternionf()
                            )
                    );
                    entidade.setPersistent(true);
                }
        );

        bigornasOcupadas.put(key, display);
        salvarForja(key, display, itemForja);

        if (item.getAmount() == 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }

        player.sendMessage("§6[Forja] §eItem colocado sobre a bigorna!");
    }

    private void retirarItemDaForja(Player player, Location key) {
        ItemDisplay display = bigornasOcupadas.get(key);

        if (display == null || !display.isValid()) {
            bigornasOcupadas.remove(key);
            removerDadosForja(key);
            player.sendMessage("§cNão há item nesta bigorna.");
            return;
        }

        ItemStack item = display.getItemStack();

        if (item == null || item.getType().isAir()) {
            display.remove();
            bigornasOcupadas.remove(key);
            removerDadosForja(key);
            player.sendMessage("§cNão há item para retirar.");
            return;
        }

        ItemStack itemRetirado = item.clone();
        itemRetirado.setAmount(1);

        Map<Integer, ItemStack> restantes =
                player.getInventory().addItem(itemRetirado);

        for (ItemStack restante : restantes.values()) {
            player.getWorld().dropItemNaturally(
                    player.getLocation(), restante
            );
        }

        // Remove o display registrado.
        display.remove();

        // Remove displays extras na posição visual desta mesma bigorna.
        Location centroDisplay = key.clone().add(0.5, 1.05, 0.5);

        for (ItemDisplay extra : key.getWorld().getEntitiesByClass(ItemDisplay.class)) {
            if (extra.getLocation().distanceSquared(centroDisplay) < 0.04) {
                extra.remove();
            }
        }

        bigornasOcupadas.remove(key);
        removerDadosForja(key);

        player.sendMessage("§6[Forja] §eVocê retirou o item da bigorna!");
    }


    private void salvarForja(
            Location key, ItemDisplay display, ItemStack item) {

        String caminho = caminho(key);

        dados.set(caminho + ".world", key.getWorld().getName());
        dados.set(caminho + ".x", key.getBlockX());
        dados.set(caminho + ".y", key.getBlockY());
        dados.set(caminho + ".z", key.getBlockZ());
        dados.set(caminho + ".entity", display.getUniqueId().toString());
        dados.set(caminho + ".item", item);

        plugin.getLogger().info(
                "[AeternumForja] Salvando item na bigorna em: "
                        + key.getWorld().getName()
                        + " " + key.getBlockX()
                        + " " + key.getBlockY()
                        + " " + key.getBlockZ()
        );

        salvarArquivo();
    }


    private void removerDadosForja(Location key) {
        dados.set(caminho(key), null);
        salvarArquivo();
    }

    private String caminho(Location key) {
        return "forjas." + key.getWorld().getUID()
                + "_" + key.getBlockX()
                + "_" + key.getBlockY()
                + "_" + key.getBlockZ();
    }

    private void carregarForjas() {
        if (dados.getConfigurationSection("forjas") == null) {
            plugin.getLogger().info(
                    "[AeternumForja] Nenhuma forja salva para recuperar."
            );
            return;
        }

        for (String id : dados.getConfigurationSection("forjas").getKeys(false)) {
            String caminho = "forjas." + id;
            String mundoNome = dados.getString(caminho + ".world");

            World mundo = mundoNome == null
                    ? null
                    : plugin.getServer().getWorld(mundoNome);

            if (mundo == null) {
                plugin.getLogger().warning(
                        "[AeternumForja] Mundo indisponível para a forja " + id
                );
                continue;
            }

            int x = dados.getInt(caminho + ".x");
            int y = dados.getInt(caminho + ".y");
            int z = dados.getInt(caminho + ".z");
            Location key = new Location(mundo, x, y, z);

            ItemStack item = dados.getItemStack(caminho + ".item");

            if (item == null || item.getType().isAir()) {
                plugin.getLogger().warning(
                        "[AeternumForja] Item inválido na forja " + id
                );
                continue;
            }

            String uuidTexto = dados.getString(caminho + ".entity");
            ItemDisplay display = null;

            if (uuidTexto != null) {
                try {
                    UUID uuid = UUID.fromString(uuidTexto);

                    if (mundo.getEntity(uuid) instanceof ItemDisplay encontrado
                            && encontrado.isValid()) {
                        display = encontrado;
                    }
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning(
                            "[AeternumForja] UUID inválido na forja " + id
                    );
                }
            }

            if (display == null || !display.isValid()) {
                Location localDisplay = key.clone().add(0.5, 1.05, 0.5);
                ItemStack itemRestaurado = item.clone();

                display = mundo.spawn(
                        localDisplay,
                        ItemDisplay.class,
                        entidade -> {
                            entidade.setItemStack(itemRestaurado);
                            entidade.setItemDisplayTransform(
                                    ItemDisplayTransform.FIXED
                            );
                            entidade.setTransformation(
                                    new Transformation(
                                            new Vector3f(0, 0, 0),
                                            new Quaternionf().rotateX(
                                                    (float) Math.toRadians(90)
                                            ),
                                            new Vector3f(0.65f, 0.65f, 0.65f),
                                            new Quaternionf()
                                    )
                            );
                            entidade.setPersistent(true);
                        }
                );

                dados.set(caminho + ".entity", display.getUniqueId().toString());
                salvarArquivo();
            }

            bigornasOcupadas.put(key, display);

            plugin.getLogger().info(
                    "[AeternumForja] Forja recuperada em "
                            + mundo.getName() + " " + x + " " + y + " " + z
            );
        }
    }


    private void salvarArquivo() {
        try {
            if (!arquivoForjas.exists()) {
                arquivoForjas.getParentFile().mkdirs();
                arquivoForjas.createNewFile();
            }
            dados.save(arquivoForjas);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "Não foi possível salvar forjas.yml: " + e.getMessage()
            );
        }
    }

}




package aeternumrp.aeternumForja;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ForjaMinigame implements Listener {

    private static final int TAMANHO_INVENTARIO = 27;
    private static final int LINHA_BARRA = 1;
    private static final int POSICAO_INICIAL = 0;
    private static final int POSICOES_BARRA = 9;

    private final JavaPlugin plugin;
    private final Map<UUID, Partida> partidas = new HashMap<>();

    public ForjaMinigame(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void iniciar(Player player) {
        UUID uuid = player.getUniqueId();

        if (partidas.containsKey(uuid)) {
            player.sendMessage("§cVocê já está em um minigame de forja!");
            return;
        }

        Inventory inventario = Bukkit.createInventory(
                null,
                TAMANHO_INVENTARIO,
                "§6Minigame de Forja"
        );

        Partida partida = new Partida(inventario);
        partidas.put(uuid, partida);

        desenhar(partida);
        player.openInventory(inventario);

        int intervalo = Math.max(
                1,
                plugin.getConfig().getInt("minigame.barra.intervalo_ticks", 2)
        );

        partida.tarefa = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> atualizar(player),
                0L,
                intervalo
        );

        player.sendMessage(
                "§eClique na barra quando o indicador estiver na zona verde!"
        );
    }

    private void atualizar(Player player) {
        Partida partida = partidas.get(player.getUniqueId());

        if (partida == null) {
            return;
        }

        if (!player.isOnline()
                || !player.getOpenInventory().getTopInventory()
                .equals(partida.inventario)) {
            encerrar(player.getUniqueId());
            return;
        }

        partida.posicao += partida.direcao;

        if (partida.posicao >= POSICOES_BARRA - 1) {
            partida.posicao = POSICOES_BARRA - 1;
            partida.direcao = -1;
        } else if (partida.posicao <= 0) {
            partida.posicao = 0;
            partida.direcao = 1;
        }

        desenhar(partida);
    }

    private void desenhar(Partida partida) {
        Inventory inv = partida.inventario;
        inv.clear();

        int zonaInicio = limitarPosicao(
                plugin.getConfig().getInt("minigame.barra.zona_inicio", 3)
        );
        int zonaFim = limitarPosicao(
                plugin.getConfig().getInt("minigame.barra.zona_fim", 5)
        );

        int criticoInicio = limitarPosicao(
                plugin.getConfig().getInt("minigame.barra.critico_inicio", 4)
        );
        int criticoFim = limitarPosicao(
                plugin.getConfig().getInt("minigame.barra.critico_fim", 4)
        );

        for (int coluna = 0; coluna < POSICOES_BARRA; coluna++) {
            Material material = Material.GRAY_STAINED_GLASS_PANE;
            String nome = "§7Fora da zona";

            if (coluna >= zonaInicio && coluna <= zonaFim) {
                material = Material.LIME_STAINED_GLASS_PANE;
                nome = "§aZona de acerto";
            }

            if (coluna >= criticoInicio && coluna <= criticoFim) {
                material = Material.YELLOW_STAINED_GLASS_PANE;
                nome = "§eZona de acerto crítico";
            }

            inv.setItem(
                    LINHA_BARRA * 9 + coluna,
                    criarItem(material, nome)
            );
        }

        inv.setItem(
                LINHA_BARRA * 9 + partida.posicao,
                criarItem(Material.ANVIL, "§eIndicador — clique para martelar!")
        );

        inv.setItem(
                4,
                criarItem(
                        Material.PAPER,
                        "§ePontuação: §f" + partida.pontos
                )
        );

        Material ferramenta = Material.matchMaterial(
                plugin.getConfig().getString(
                        "minigame.ferramenta",
                        "IRON_INGOT"
                )
        );

        if (ferramenta == null || ferramenta.isAir()) {
            ferramenta = Material.IRON_INGOT;
        }

        int maxTentativas = Math.max(
                1,
                plugin.getConfig().getInt("minigame.tentativas", 12)
        );

        inv.setItem(
                22,
                criarItem(
                        ferramenta,
                        "§6Tentativa " + (partida.tentativas + 1)
                                + "/" + maxTentativas
                )
        );
    }

    private int limitarPosicao(int posicao) {
        return Math.max(0, Math.min(POSICOES_BARRA - 1, posicao));
    }

    private ItemStack criarItem(Material material, String nome) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(nome);
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Partida partida = partidas.get(player.getUniqueId());

        if (partida == null
                || !event.getView().getTopInventory()
                .equals(partida.inventario)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(partida.inventario)) {
            return;
        }

        if (event.getSlot() / 9 != LINHA_BARRA) {
            return;
        }

        int zonaInicio = limitarPosicao(
                plugin.getConfig().getInt("minigame.barra.zona_inicio", 3)
        );
        int zonaFim = limitarPosicao(
                plugin.getConfig().getInt("minigame.barra.zona_fim", 5)
        );
        int criticoInicio = limitarPosicao(
                plugin.getConfig().getInt("minigame.barra.critico_inicio", 4)
        );
        int criticoFim = limitarPosicao(
                plugin.getConfig().getInt("minigame.barra.critico_fim", 4)
        );

        int maxTentativas = Math.max(
                1,
                plugin.getConfig().getInt("minigame.tentativas", 12)
        );

        int pontosAcerto = plugin.getConfig().getInt(
                "minigame.pontos_por_acerto", 1
        );
        int pontosCritico = plugin.getConfig().getInt(
                "minigame.pontos_por_critico", 2
        );

        partida.tentativas++;

        if (partida.posicao >= criticoInicio
                && partida.posicao <= criticoFim
                && partida.posicao >= zonaInicio
                && partida.posicao <= zonaFim) {
            partida.pontos += Math.max(0, pontosCritico);
            player.sendMessage(
                    "§e✦ ACERTO CRÍTICO! §6+" + pontosCritico + " pontos"
            );
        } else if (partida.posicao >= zonaInicio
                && partida.posicao <= zonaFim) {
            partida.pontos += Math.max(0, pontosAcerto);
            player.sendMessage(
                    "§aMartelada precisa! §e+" + pontosAcerto + " ponto(s)"
            );
        } else {
            player.sendMessage("§cMartelada fora da zona!");
        }

        if (partida.tentativas >= maxTentativas) {
            finalizar(player, partida, maxTentativas);
        } else {
            desenhar(partida);
        }
    }

    private void finalizar(
            Player player,
            Partida partida,
            int maxTentativas
    ) {
        encerrar(player.getUniqueId());
        player.closeInventory();

        player.sendMessage("§6§lFORJA CONCLUÍDA!");
        player.sendMessage(
                "§ePontuação final: §f" + partida.pontos
        );

        if (partida.pontos >= 10) {
            player.sendMessage("§aQualidade: Excelente");
        } else if (partida.pontos >= 7) {
            player.sendMessage("§eQualidade: Boa");
        } else {
            player.sendMessage("§cQualidade: Péssima");
        }
    }

    @EventHandler
    public void aoFechar(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Partida partida = partidas.get(player.getUniqueId());

        if (partida != null
                && event.getInventory().equals(partida.inventario)) {
            encerrar(player.getUniqueId());
        }
    }

    private void encerrar(UUID uuid) {
        Partida partida = partidas.remove(uuid);

        if (partida != null && partida.tarefa != null) {
            partida.tarefa.cancel();
        }
    }

    private static final class Partida {
        private final Inventory inventario;
        private int posicao = POSICAO_INICIAL;
        private int direcao = 1;
        private int tentativas;
        private int pontos;
        private BukkitTask tarefa;

        private Partida(Inventory inventario) {
            this.inventario = inventario;
        }
    }
}

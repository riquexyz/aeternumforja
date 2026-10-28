
package aeternumrp.aeternumForja;

import org.bukkit.plugin.java.JavaPlugin;

public final class AeternumForja extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Não foi possível criar a pasta do plugin!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        saveResource("receitas.yml", false);

        getServer().getPluginManager().registerEvents(
                new BigornaListener(this),
                this
        );

        getLogger().info("AeternumForja foi ativado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AeternumForja foi desativado!");
    }
}

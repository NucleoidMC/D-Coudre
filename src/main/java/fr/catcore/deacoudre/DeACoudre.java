package fr.catcore.deacoudre;

import fr.catcore.deacoudre.game.DeACoudreConfig;
import fr.catcore.deacoudre.game.DeACoudreWaiting;
import net.fabricmc.api.ModInitializer;
import xyz.nucleoid.plasmid.api.game.GameType;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeACoudre implements ModInitializer {

    public static final String ID = "deacoudre";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    @Override
    public void onInitialize() {
        GameType.register(
                Identifier.of(ID, "deacoudre"),
                DeACoudreConfig.CODEC,
                DeACoudreWaiting::open
        );
    }
}

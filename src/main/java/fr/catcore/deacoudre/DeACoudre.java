package fr.catcore.deacoudre;

import fr.catcore.deacoudre.game.DeACoudreConfig;
import fr.catcore.deacoudre.game.DeACoudreWaiting;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.plasmid.api.game.GameType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.api.game.GameTypes;

public class DeACoudre implements ModInitializer {

    public static final String ID = "deacoudre";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    @Override
    public void onInitialize() {
        GameTypes.register(
                Identifier.fromNamespaceAndPath(ID, "deacoudre"),
                DeACoudreConfig.CODEC,
                DeACoudreWaiting::open
        );
    }
}

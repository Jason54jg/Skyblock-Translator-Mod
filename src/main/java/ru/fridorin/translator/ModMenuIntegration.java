package ru.fridorin.translator;

import ru.fridorin.translator.gui.TranslatorConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TranslatorConfigScreen::createScreen;
    }
}

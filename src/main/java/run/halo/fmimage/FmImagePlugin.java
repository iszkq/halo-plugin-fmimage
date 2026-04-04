package run.halo.fmimage;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class FmImagePlugin extends BasePlugin {
    public FmImagePlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}

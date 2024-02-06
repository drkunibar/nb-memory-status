package io.github.drkunibar.netbeans.nb.memory.status;

import java.awt.Component;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = StatusLineElementProvider.class)
public class StatuslineMemoryUsage implements StatusLineElementProvider {

    private final StatuslineMemoryUsagePanel panel;

    public StatuslineMemoryUsage() {
        this.panel = new StatuslineMemoryUsagePanel();
    }

    @Override
    public Component getStatusLineElement() {
        return panel;
    }

}

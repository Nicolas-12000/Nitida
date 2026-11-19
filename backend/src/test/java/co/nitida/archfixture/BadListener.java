package co.nitida.archfixture;

import org.springframework.modulith.events.ApplicationModuleListener;

public class BadListener {
    @ApplicationModuleListener
    void on(Object event) {}
}

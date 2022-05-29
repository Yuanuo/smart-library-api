package org.appxi.smartlib.dao;

import org.appxi.event.Event;
import org.appxi.event.EventBus;
import org.appxi.event.EventType;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.URL;
import java.nio.file.Path;

public abstract class BeansContext {
    private static final Object _initBeans = new Object();
    private static AnnotationConfigApplicationContext beans;

    private static EventBus eventBus;
    public static final EventType<BeansEvent> BEANS_READY = new EventType<>(Event.ANY, "SPRING_BEANS_READY");
    public static final EventType<BeansEvent> BEANS_FAILS = new EventType<>(Event.ANY, "SPRING_BEANS_FAILS");

    public static void setupInitialize(EventBus eventBus, Path solrHome, Path confHome) {
        BeansContext.eventBus = eventBus;
        BeansConfig.solrHome = solrHome;
        BeansConfig.confHome = confHome;
        beans();
    }

    private static BeanFactory beans() {
        if (null != beans)
            return beans;
        synchronized (_initBeans) {
            if (null != beans)
                return beans;
            try {
                beans = new AnnotationConfigApplicationContext(BeansConfig.class) {
                    @Override
                    public Resource[] getResources(String locationPattern) {
                        if ("classpath*:org/appxi/smartlib/dao/**/*.class".equals(locationPattern)) {
                            URL url = BeansContext.class.getResource("/org/appxi/smartlib/dao/PiecesRepository.class");
                            return null == url ? new Resource[0] : new Resource[]{new UrlResource(url)};
                        }
                        return new Resource[0];
                    }
                };
//                App.app().eventBus.fireEvent(new GenericEvent(GenericEvent.BEANS_READY));
//                App.app().logger.warn(StringHelper.concat("beans init after: ",
//                        System.currentTimeMillis() - App.app().startTime));
            } catch (Throwable t) {
                t.printStackTrace();
                if (null != eventBus) {
                    eventBus.fireEvent(new BeansEvent(BEANS_FAILS, t));
                }
                return beans;
            }
            if (null != eventBus) {
                eventBus.fireEvent(new BeansEvent(BEANS_READY));
            }
        }
        return beans;
    }

    public static <T> T getBean(Class<T> requiredType) {
        try {
            return beans().getBean(requiredType);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class BeansEvent extends Event {
        BeansEvent(EventType<BeansEvent> eventType) {
            super(eventType);
        }

        BeansEvent(EventType<BeansEvent> eventType, Object data) {
            super(eventType, data);
        }
    }
}

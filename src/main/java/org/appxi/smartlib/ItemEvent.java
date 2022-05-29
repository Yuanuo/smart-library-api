package org.appxi.smartlib;

import org.appxi.event.Event;
import org.appxi.event.EventType;

public class ItemEvent extends Event {
    private static final long serialVersionUID = 8183534581997098428L;

    public static final EventType<ItemEvent> ITEM_EVENT = new EventType<>(Event.ANY, "ITEM_EVENT");
    public static final EventType<ItemEvent> VIEWING = new EventType<>(ITEM_EVENT, "ITEM_VIEWING");
    public static final EventType<ItemEvent> VISITED = new EventType<>(ITEM_EVENT, "ITEM_VISITED");

    public static final EventType<ItemEvent> EDITING = new EventType<>(ITEM_EVENT, "ITEM_EDITING");
    //
    public static final EventType<ItemEvent> CREATED = new EventType<>(ITEM_EVENT, "ITEM_CREATED");
    //
    public static final EventType<ItemEvent> RENAMED = new EventType<>(ITEM_EVENT, "ITEM_RENAMED");
    //
    public static final EventType<ItemEvent> UPDATED = new EventType<>(ITEM_EVENT, "ITEM_UPDATED");
    //
    public static final EventType<ItemEvent> DELETED = new EventType<>(ITEM_EVENT, "ITEM_DELETED");
    //
    public static final EventType<ItemEvent> MOVED = new EventType<>(ITEM_EVENT, "ITEM_MOVED");
    //
    public static final EventType<ItemEvent> RESTORED = new EventType<>(ITEM_EVENT, "ITEM_RESTORED");
    //
    public final Item item, from;

    public ItemEvent(EventType<ItemEvent> eventType, Item item) {
        this(eventType, item, null);
    }

    public ItemEvent(EventType<ItemEvent> eventType, Item item, Item from) {
        super(eventType);
        this.item = item;
        this.from = from;
    }
}

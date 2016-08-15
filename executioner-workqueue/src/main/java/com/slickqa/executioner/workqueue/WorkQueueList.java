package com.slickqa.executioner.workqueue;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * A specialty data structure that can improve speed of looking up items.
 */
public class WorkQueueList extends LinkedList<WorkQueueItem> {
    protected Map<String, Integer> idMap;
    protected Map<Set<String>, LinkedList<Integer>> requirementSetMap;

    public WorkQueueList() {
        super();
        idMap = new HashMap<>();
        requirementSetMap = new HashMap<>();
    }



    @Override
    public boolean add(WorkQueueItem workQueueItem) {
        boolean retval = super.add(workQueueItem);
        if(retval && workQueueItem != null) {
            int index = size() - 1;
            if (workQueueItem.toJsonObject().containsKey("id")) {
                idMap.put(workQueueItem.toJsonObject().getString("id"), index);
            }
            if(!requirementSetMap.containsKey(workQueueItem.getRequirements())) {
                requirementSetMap.put(workQueueItem.getRequirements(), new LinkedList<>());
            }
            requirementSetMap.get(workQueueItem.getRequirements()).add(index);
        }
        return retval;
    }

    @Override
    public WorkQueueItem remove(int index) {
        WorkQueueItem item = super.remove(index);
        if(item.toJsonObject().containsKey("id")) {
            idMap.remove(item.toJsonObject().getString("id"));
        }
        if(requirementSetMap.containsKey(item.getRequirements())) {
            requirementSetMap.get(item.getRequirements()).remove(new Integer(index));
        }
        return item;
    }

    public WorkQueueItem remove(JsonObject item) {
        if(isEmpty()) {
            return null;
        }

        if(item.containsKey("id") && idMap.containsKey(item.getString("id"))) {
            return remove(idMap.get(item.getString("id")).intValue());
        }

        // well there's no id, let's see if we can make the list smaller by using the requirement set
        WorkQueueItem temp = new WorkQueueItem(item);
        if(requirementSetMap.containsKey(temp.getRequirements())) {
            for(int potentialIndex : requirementSetMap.get(temp.getRequirements())) {
                if(get(potentialIndex).matches(item)) {
                    return remove(potentialIndex);
                }
            }
        }

        // this is unfortunate, we have to iterate through the whole list
        for(int index = 0; index < size(); index++) {
            if(get(index).matches(item)) {
                return remove(index);
            }
        }

        return null;
    }
}

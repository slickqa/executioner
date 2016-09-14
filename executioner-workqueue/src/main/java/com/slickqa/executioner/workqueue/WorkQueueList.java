package com.slickqa.executioner.workqueue;


import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

/**
 * A specialty data structure that can improve speed of looking up items.
 */
public class WorkQueueList extends LinkedHashMap<String, WorkQueueItem> {
    private Map<Set<String>, List<String>> requirementSetMap;
    private Logger log;

    public WorkQueueList() {
        super();
        requirementSetMap = new HashMap<>();
        log = LoggerFactory.getLogger(this.getClass());
    }

    public Set<Set<String>> getRequirementSets() {
        return requirementSetMap.keySet();
    }

    public List<String> getIdsByRequirmentSet(Set<String> reqSet) {
        return requirementSetMap.get(reqSet);
    }

    private void removeFromRequirementSetMap(WorkQueueItem item) {
        if(requirementSetMap.containsKey(item.getRequirements())) {
            requirementSetMap.get(item.getRequirements()).remove(item.getId());
        } else {
            if(item.getId() != null) {
                requirementSetMap.forEach((Set<String> key, List<String> value) -> value.remove(item.getId()));
            }
            log.warn("Found work queue item that matches provides, but it isn't indexed? \nMatching requirement set: " + String.join(", ", item.getRequirements()));
            StringBuilder allReqSets = new StringBuilder();
            for(Set<String> reqset : requirementSetMap.keySet()) {
                allReqSets.append("\t");
                allReqSets.append(String.join(", ", reqset));
                allReqSets.append("\n");
            }
            log.warn("Existing Requirment Sets:\n" + allReqSets.toString());
        }

    }

    public WorkQueueItem remove(JsonObject item) {
        if(isEmpty()) {
            return null;
        }

        WorkQueueItem temp = new WorkQueueItem(item);
        removeFromRequirementSetMap(temp);
        // it has to have an id, otherwise it will never match anything in the queue
        return remove(item.getString("id"));
    }

    public WorkQueueItem removeFirstMatchingItem(Set<String> provides) {
        WorkQueueItem retval = null;

        String matchingId = null;
        for(WorkQueueItem potential : values()) {
            if(provides.containsAll(potential.getRequirements())) {
                matchingId = potential.getId();
                break;
            }
        }
        if(matchingId != null) {
            retval = remove(matchingId);
            removeFromRequirementSetMap(retval);
        }
        return retval;
    }

    public void add(WorkQueueItem item) {
        put(item.getId(), item);

        if(!requirementSetMap.containsKey(item.getRequirements())) {
            requirementSetMap.put(item.getRequirements(), new LinkedList<>());
        }
        requirementSetMap.get(item.getRequirements()).add(item.getId());
    }
}

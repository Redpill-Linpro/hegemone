package hegemone.sensors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DataSubmitter {
    static List<DataConsumer> consumerList = new ArrayList<>();
    /**
     * Submit data to some kind of data consumer
     */
    public static void submit(String data) {
        consumerList.forEach(c -> c.accept(data));
    }

    public static void register(DataConsumer consumer) {
        consumerList.add(consumer);
    }
}

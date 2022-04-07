package hegemone.sensors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLogger implements DataConsumer {
    private static final Logger log = LoggerFactory.getLogger("hegemone.sensors.datalogger");

    @Override
    public void accept(String data) {
        log.debug(data);
    }
}

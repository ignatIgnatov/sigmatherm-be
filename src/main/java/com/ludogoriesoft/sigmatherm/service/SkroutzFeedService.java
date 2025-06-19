package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.skroutz.MyWebStore;
import com.ludogoriesoft.sigmatherm.entity.ProductEntity;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class SkroutzFeedService {

    private static final String FEED_PATH = "/app/feeds/skroutz_feed.xml";

    public void updateFeed(File sourceXmlFile, List<ProductEntity> updatedProducts) throws Exception {
        if (!sourceXmlFile.exists()) {
            throw new FileNotFoundException("Source XML file not found: " + sourceXmlFile.getAbsolutePath());
        }
        JAXBContext context = JAXBContext.newInstance(MyWebStore.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        MyWebStore store = (MyWebStore) unmarshaller.unmarshal(sourceXmlFile);

        for (ProductEntity p : updatedProducts) {
            store.getProducts().forEach(product -> {
                if (p.getId().equals(product.getId())) {
                    product.setQuantity(p.getStock());
                }
            });
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        store.setCreatedAt(now);

        File output = new File(FEED_PATH);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(store, output);

        log.info("Feed updated at: " + FEED_PATH);
    }

    public File getFeedFile() {
        return Paths.get(FEED_PATH).toFile();
    }
}




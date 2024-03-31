package com.web.testselsup;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@Slf4j
public class CrptApi {

    public static void main(String[] args) {
        SpringApplication.run(CrptApi.class, args);
    }
}

// model
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Document {
    @Id
    private String doc_id;
    private String doc_status;
    private String doc_type;
    private boolean importRequest;
    private String owner_inn;
    private String participant_inn;
    private String producer_inn;
    private String production_date;
    private String production_type;
    private String reg_date;
    private String reg_number;

    @OneToOne(cascade = CascadeType.ALL)
    private Description description;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Product> products;
}

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
class Description {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long descriptionId;

    private String participantInn;
}

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    private String certificate_document;
    private String certificate_document_date;
    private String certificate_document_number;
    private String owner_inn;
    private String producer_inn;
    private String production_date;
    private String tnved_code;
    private String uit_code;
    private String uitu_code;
}

// repository
@Repository
interface DocumentRepository extends JpaRepository<Document, String> {}

// dto
@Data
@AllArgsConstructor
@NoArgsConstructor
class DocumentRequest {
    private String doc_id;
    private String doc_status;
    private String doc_type;
    private boolean importRequest;
    private String owner_inn;
    private String participant_inn;
    private String producer_inn;
    private String production_date;
    private String production_type;
    private String reg_date;
    private String reg_number;


    private DescriptionDto description;

    private List<ProductDto> products;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class DescriptionDto {
    private Long descriptionId;
    private String participantInn;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class ProductDto {
    private Long productId;

    private String certificate_document;
    private String certificate_document_date;
    private String certificate_document_number;
    private String owner_inn;
    private String producer_inn;
    private String production_date;
    private String tnved_code;
    private String uit_code;
    private String uitu_code;
}



// controller
@RestController
@RequestMapping("/api/v3/lk/documents/create")
@PropertySource("application.properties")
class DocumentController {

    private final DocumentService documentService;
    private final TimeUnit timeUnit;
    private final int requestLimit;

    public DocumentController(DocumentService documentService,
                              @Value("${request.limit}") int requestLimit,
                              @Value("${time.unit}") TimeUnit timeUnit) {
        this.documentService = documentService;
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
    }

    @PostMapping
    public String createDocument(@RequestBody DocumentRequest documentRequest) {
        documentService.createDocument(documentRequest, timeUnit, requestLimit);
        return "Document Created Successfully";
    }
}


// service
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
class DocumentService {
    private final DocumentRepository documentRepository;
    private final Object lock = new Object();
    private long lastRequestTime = System.currentTimeMillis();
    private int requestCounter = 0;

    public void createDocument(DocumentRequest documentRequest, TimeUnit timeUnit, int requestLimit) {
        synchronized (lock) {
            long currentTime = System.currentTimeMillis();
            long interval = timeUnit.toMillis(1); // Convert time unit to milliseconds
            if (currentTime - lastRequestTime >= interval) {

                requestCounter = 0;
            }
            while (requestCounter >= requestLimit) {

                try {
                    lock.wait(); 
                } catch (InterruptedException e) {
                    log.error("Thread interrupted while waiting: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }

            lastRequestTime = currentTime;
            requestCounter++;
        }

        Document document = Document.builder()
                .doc_id(documentRequest.getDoc_id())
                .doc_status(documentRequest.getDoc_status())
                .doc_type(documentRequest.getDoc_type())
                .importRequest(documentRequest.isImportRequest())
                .owner_inn(documentRequest.getOwner_inn())
                .participant_inn(documentRequest.getParticipant_inn())
                .producer_inn(documentRequest.getProducer_inn())
                .production_date(documentRequest.getProduction_date())
                .production_type(documentRequest.getProduction_type())
                .reg_date(documentRequest.getReg_date())
                .reg_number(documentRequest.getReg_number())
                .build();

        List<Product> products = documentRequest.getProducts()
                .stream()
                .map(this::mapToProductDto)
                .toList();

        document.setProducts(products);

        DescriptionDto descriptionDto = documentRequest.getDescription();
        document.setDescription(toDescriptionDto(descriptionDto));

        documentRepository.save(document);
        log.info("Document {} is saved", document.getDoc_id());

        synchronized (lock) {
            lock.notify();
        }
    }




    private Product mapToProductDto(ProductDto productDto) {
        Product product = new Product();
        product.setCertificate_document(productDto.getCertificate_document());
        product.setCertificate_document_date(productDto.getCertificate_document_date());
        product.setCertificate_document_number(productDto.getCertificate_document_number());
        product.setOwner_inn(productDto.getOwner_inn());
        product.setProducer_inn(productDto.getProducer_inn());
        product.setProduction_date(productDto.getProduction_date());
        product.setTnved_code(productDto.getTnved_code());
        product.setUit_code(productDto.getUit_code());
        product.setUitu_code(productDto.getUitu_code());

        return product;
    }

    private Description toDescriptionDto(DescriptionDto descriptionDto) {
        Description description = new Description();
        description.setParticipantInn(descriptionDto.getParticipantInn());

        return description;
    }
}

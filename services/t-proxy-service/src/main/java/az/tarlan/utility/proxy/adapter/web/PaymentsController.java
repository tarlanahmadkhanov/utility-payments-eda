package az.tarlan.utility.proxy.adapter.web;

import az.tarlan.utility.proxy.api.PaymentsApi;
import az.tarlan.utility.proxy.api.model.CreatePaymentRequest;
import az.tarlan.utility.proxy.api.model.Payment;
import az.tarlan.utility.proxy.application.AcceptPaymentCommand;
import az.tarlan.utility.proxy.application.AcceptPaymentResult;
import az.tarlan.utility.proxy.application.AcceptPaymentService;
import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.domain.PaymentStatus;
import az.tarlan.utility.proxy.domain.ServiceType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
public class PaymentsController implements PaymentsApi {

    private static final String RETRY_AFTER_SECONDS = "1";

    private final AcceptPaymentService acceptPaymentService;
    private final PaymentRepository paymentRepository;

    public PaymentsController(AcceptPaymentService acceptPaymentService, PaymentRepository paymentRepository) {
        this.acceptPaymentService = acceptPaymentService;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public ResponseEntity<Payment> createPayment(String idempotencyKey, String xCustomerId,
                                                 CreatePaymentRequest createPaymentRequest) {
        AcceptPaymentCommand command = new AcceptPaymentCommand(
                xCustomerId,
                idempotencyKey,
                createPaymentRequest.getAccountNumber(),
                ServiceType.valueOf(createPaymentRequest.getServiceType().getValue()),
                createPaymentRequest.getAmountMinor(),
                createPaymentRequest.getCurrency());

        return switch (acceptPaymentService.accept(command)) {
            case AcceptPaymentResult.Accepted accepted -> {
                ResponseEntity.BodyBuilder response = ResponseEntity.accepted()
                        .location(URI.create("/payments/" + accepted.payment().id()));
                addRetryAfter(response, accepted.payment().status());
                yield response.body(PaymentApiMapper.toApi(accepted.payment()));
            }
            case AcceptPaymentResult.IdempotencyKeyReused reused -> throw new IdempotencyKeyReusedException();
        };
    }

    @Override
    public ResponseEntity<Payment> getPayment(UUID id, String xCustomerId) {
        var payment = paymentRepository.findByIdAndCustomerId(id, xCustomerId)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        addRetryAfter(response, payment.status());
        return response.body(PaymentApiMapper.toApi(payment));
    }

    private static void addRetryAfter(ResponseEntity.BodyBuilder response, PaymentStatus status) {
        if (!status.isTerminal()) {
            response.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        }
    }
}

package org.uengine.meter.billing;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.uengine.meter.billing.kafka.BillingProcessor;
import org.uengine.meter.billing.kb.KBApi;
import org.uengine.meter.billing.kb.KBConfig;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/meter/billing")
public class BillingController {

    @Autowired
    private KBConfig kbConfig;

    @Autowired
    private KBApi kbApi;

    @Autowired
    private BillingProcessor billingProcessor;

    @Autowired
    private BillingService billingService;

    @Autowired
    private BillingRedisRepository billingRedisRepository;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final Log logger = LogFactory.getLog(getClass());

    @GetMapping(value = "/tenant", produces = "application/json")
    public Object tenant() {
        return kbApi.getTanantByApiKey(kbConfig.getApikey());
    }

    @PostMapping(value = "/event", produces = "application/json")
    public String receiveBillingEvent(@RequestBody String event) {
        billingProcessor.sendBillingMessage(event);
        return "";
    }
    //getUserSubscriptions

    @GetMapping(value = "/subscriptions", produces = "application/json")
    public Object getUserSubscriptions(@RequestParam(value = "user", required = false) String user) {
        return billingRedisRepository.findByUserName(user);
        //return billingService.getUserSubscriptions(user, true);
    }

    @GetMapping("/emitter")
    public SseEmitter handle(HttpServletRequest request,
                             HttpServletResponse response
    ) throws Exception {
        try {

            SseEmitter emitter = new SseEmitter(360_000L); //360 sec.
            emitters.add(emitter);

            emitter.onCompletion(() -> this.emitters.remove(emitter));
            emitter.onTimeout(() -> this.emitters.remove(emitter));

            logger.info("emitter counts: " + emitters.size());

            return emitter;
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unauthorized");
            return null;
        }
    }

    public void emitterSend(Map event) throws Exception {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        this.emitters.forEach(emitter -> {
            try {
                emitter.send(event);
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }
}

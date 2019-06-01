package hello;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * For remote debugging, use the following command:
 * java -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=8000,suspend=n -jar gs-rest-service-0.1.0.jar

 * 
 * @author yilinglu
 *
 */
@RestController
public class BuildOrderController<T extends Serializable> {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @RequestMapping("/greeting")
    public Greeting greeting(@RequestParam(value="name", defaultValue="World") String name) {
        return new Greeting(counter.incrementAndGet(),
                            String.format(template, name));
    }

    @RequestMapping(value = "/order", method=RequestMethod.POST, 
    		consumes = {MediaType.APPLICATION_XML_VALUE})
    public @ResponseBody ResponseEntity<String> post(@RequestBody String raw) {
    	System.out.println(raw);
        return new ResponseEntity<String>(raw, HttpStatus.OK);
    }
}

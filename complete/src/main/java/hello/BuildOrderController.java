package hello;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * For remote debugging, use the following command:
 * java -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=8081,suspend=n -jar gs-rest-service-0.1.0.jar

 * 
 * @author yilinglu
 *
 */
@RestController
public class BuildOrderController<T extends Serializable> {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();
    
    private HashMap<String, ArrayList<String>> deps = new HashMap();

    @RequestMapping("/greeting")
    public Greeting greeting(@RequestParam(value="name", defaultValue="World") String name) {
        return new Greeting(counter.incrementAndGet(),
                            String.format(template, name));
    }

    @RequestMapping(value = "/input", method=RequestMethod.POST, 
    		consumes = {MediaType.APPLICATION_XML_VALUE})
    public @ResponseBody ResponseEntity<String> post(@RequestBody String raw) {
    	
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder;
        
		try {
			builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(raw.getBytes()));
			NodeList projectNodes = doc.getElementsByTagName("project");
			if(projectNodes.getLength() > 0) {
				
				Node projectNode = projectNodes.item(0);
				String projectId = buildIdFromDepNode(projectNode);
				if(StringUtils.isNotEmpty(projectId)) {
					
					if(deps.get(projectId) != null) {
						System.out.println(String.format("Project Id %s is overwritten by latest pom file.", projectId));
					} 
					System.out.println(String.format("Project Id: %s ", projectId));
					deps.put(projectId, new ArrayList<String>());
					
					if(doc.getElementsByTagName("dependencies").getLength() > 0) {
						NodeList dependencies = doc.getElementsByTagName("dependencies").item(0).getChildNodes();
						for(int i = 0; i < dependencies.getLength(); i++) {
							String depId = buildIdFromDepNode(dependencies.item(i));
							if(StringUtils.isNotEmpty(depId)) {
								deps.get(projectId).add(depId);
								System.out.println(String.format("dep: %s", depId));
							}
						}
					}
					
				}
				
			}
			
		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
    	
        return new ResponseEntity<String>(raw, HttpStatus.OK);
    }
    
    /**
     * Given a node that has <groupId> <artifactId> and <version> (optional) as child nodes
     * Build a unique identifier
     * 
     * @param depNode
     * @return
     */
    private String buildIdFromDepNode(Node parentNode) {
    	List<String> list = new ArrayList<String>();
    	
		NodeList children = parentNode.getChildNodes();
    	
		for(int i=0; i< children.getLength() && list.size() < 3; i++) {
			Node child = children.item(i);
			
			if(child.getNodeType() == Node.ELEMENT_NODE) {
				if (!StringUtils.isEmpty(child.getTextContent())) {
					if(child.getNodeName() == "groupId") {
						list.add(0, child.getTextContent());
					} else if (child.getNodeName() == "artifactId") {
						list.add(1, child.getTextContent());
					} else if (child.getNodeName() == "version") {
						list.add(2, child.getTextContent());
					}
				}   
				
			}
		}
    	
    	if(list.size() > 0) {
    		return StringUtils.join(list, "|");
    	} else {
    		return "";
    	}
    }
    
    
}

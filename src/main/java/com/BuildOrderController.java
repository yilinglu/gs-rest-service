package com;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * For remote debugging, use the following command:
 * java -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=8080,suspend=n -jar build-order-service-0.1.0.jar
 * 
 * @author yilinglu
 *
 */
@RestController
public class BuildOrderController<T extends Serializable> {

    private HashMap<String, List<String>> projectGavs = new HashMap<String, List<String>>();
    
    /**
     * 
     * @return An array representing the build order, index 0 is the first to be built.
     * Error message if circular dependency was found.
     */
    @RequestMapping(value = "/buildorder")
    public String findBuildOrder() {
    	List<String> buildOrder = new ArrayList<String>();
    	String error = null;
    	Set<String> keys = projectGavs.keySet();
    	
    	// Remove dependencies that are not a top level project, which is
    	// projects among which we are figuring out the build sequences.
    	for(String projectId : projectGavs.keySet()) {
    		List<String> depsList = projectGavs.get(projectId);
    		List<String> updatedDeps = 
    				depsList.stream().filter(s -> keys.contains(s))
    				.collect(Collectors.toList());
    		
    		projectGavs.put(projectId, updatedDeps);
    	}
    	
    	
    	while(projectGavs.size() > 0) {
    		int preSize = buildOrder.size();
        	//Project with zero length dependencies list is the next project that should be built.
        	for(String projectId : projectGavs.keySet()) {
        		if(projectGavs.get(projectId).size() == 0) {
        			buildOrder.add(projectId);
        		}
        	}
        	int afterSize = buildOrder.size();
        	
        	// remove all vertices that has no outgoing edges (dependencies) from the graph
        	for(String projId: buildOrder) {
        		projectGavs.remove(projId);
        	}
        	// remove all edges that are connected to these edges that has no outgoing edges.
        	if(afterSize > preSize) {
        		for(String projectId : projectGavs.keySet()) {
        			projectGavs.get(projectId).removeAll(buildOrder);
        		}
        	}
        	
        	// Did not find any vertices that has no outgoing edges, but
        	// the graph is not empty - this is a cyclic graph.
        	if(projectGavs.size() > 0 && afterSize == preSize) {
        		error = "Error: Found circular dependency";
        		projectGavs.clear();
        	}
    		
    	}
    	
    	if(error != null) {
    		return error;
    	}
    	
    	return buildOrder.toString();
    }

    /**
     * Post one or more pom.xml files that are to be placed into the CI pipeline.
     * The same pom.xml can safely be posted more than once
     * 
     * @param raw pom.xml 
     * @return
     */
    @RequestMapping(value = "/pom", method=RequestMethod.POST, 
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
				String projectId = buildGav(projectNode);
				
				if(StringUtils.isNotEmpty(projectId)) {
					
					if(projectGavs.get(projectId) != null) {
						System.out.println(String.format("Project Id %s is overwritten by latest pom file.", projectId));
					} 
					System.out.println(String.format("Project Id: %s ", projectId));
					projectGavs.put(projectId, new ArrayList<String>());
					
					if(doc.getElementsByTagName("dependencies").getLength() > 0) {
						
						NodeList dependencies = doc.getElementsByTagName("dependencies").item(0).getChildNodes();
						for(int i = 0; i < dependencies.getLength(); i++) {
							String depId = buildGav(dependencies.item(i));
							if(StringUtils.isNotEmpty(depId)) {
								projectGavs.get(projectId).add(depId);
								System.out.println(String.format("dep: %s", depId));
							}
						}
					}
					
				}
			}
			
		} catch (ParserConfigurationException | SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
	        return new ResponseEntity<String>(raw, HttpStatus.BAD_REQUEST);
	    } catch (IOException e) {
	    	// TODO Auto-generated catch block
	    	e.printStackTrace();
	    	return new ResponseEntity<String>(raw, HttpStatus.INTERNAL_SERVER_ERROR);
	    }
        
    	
        return new ResponseEntity<String>(raw, HttpStatus.OK);
    }
    
    /**
     * Given a node that has <groupId> <artifactId> and <version> (optional) as child nodes,
     * build a unique identifier that is concatenation of the element text content with | as 
     * the separator.
     * 
     * @param depNode
     * @return
     */
    private String buildGav(Node parentNode) {
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

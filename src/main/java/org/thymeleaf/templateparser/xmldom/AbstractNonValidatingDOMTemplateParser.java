package org.thymeleaf.templateparser.xmldom;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.thymeleaf.Configuration;
import org.thymeleaf.dom.Document;
import org.thymeleaf.dom.Node;
import org.thymeleaf.exceptions.ParserInitializationException;
import org.thymeleaf.exceptions.ParsingException;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.templateparser.AbstractTemplateParser;
import org.thymeleaf.templateparser.EntityResolver;
import org.thymeleaf.templateparser.ErrorHandler;
import org.thymeleaf.util.ResourcePool;
import org.thymeleaf.util.StandardDOMTranslator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * <p>
 *   Parses XML documents, using a standard non-validating DOM parser.
 * </p>
 * 
 * <p>
 *   This implementation first builds a DOM tree using the
 *   standard DOM API, and then translates this tree into a
 *   Thymeleaf-specific one. It also populates tree nodes with 
 *   basic location information (document name only).
 * </p>
 * 
 * @since 2.0.0
 * 
 * @author Daniel Fern&aacute;ndez
 * 
 */
public abstract class AbstractNonValidatingDOMTemplateParser extends AbstractTemplateParser {
    
    
    private static final String SAXPARSEEXCEPTION_BAD_ELEMENT_CONTENT =
        "The content of elements must consist of well-formed character data or markup.";
    
    private static final String SAXPARSEEXCEPTION_BAD_ELEMENT_CONTENT_EXPLANATION = 
        "The content of elements must consist of well-formed character data or " +
        "markup. A usual reason for this is that one of your elements contains " +
        "unescaped special XML symbols like '<' inside its body, which is " +
        "forbidden by XML rules. For example, if you have '<' inside a <script> tag, " +
        "you should surround your script body with commented CDATA markers (like " +
        "'/* <![CDATA[ */' and '/* ]]> */')";


    
    
    private ResourcePool<DocumentBuilder> pool;

    
    
    protected AbstractNonValidatingDOMTemplateParser(final int poolSize) {
        super();
        this.pool = createDocumentBuilders(poolSize, false);
    }
    
    
    protected ResourcePool<DocumentBuilder> getPool() {
        return this.pool;
    }

    
    protected final ResourcePool<DocumentBuilder> getNonValidatingPool() {
        return this.pool;
    }
    
    
    protected final ResourcePool<DocumentBuilder> createDocumentBuilders(final int poolSize, final boolean validating) {
        
        final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setValidating(validating);

        final List<DocumentBuilder> docBuilders = new ArrayList<DocumentBuilder>();
        
        for(int i = 0; i < poolSize; i++) {
            
            try {
                docBuilders.add(docBuilderFactory.newDocumentBuilder());
            } catch(final ParserConfigurationException e) {
                throw new ParserInitializationException("Error creating document builder", e);
            }
            
        }
        
        return new ResourcePool<DocumentBuilder>(docBuilders);
        
    }


    
    
    public final Document parseTemplate(final Configuration configuration, final String documentName, final InputSource source) {
        return parseTemplateUsingPool(configuration, documentName, source, getPool());
    }


    
    protected static final Document parseTemplateUsingPool(final Configuration configuration, final String documentName, 
            final InputSource source, final ResourcePool<DocumentBuilder> pool) {
        
        final DocumentBuilder docBuilder = pool.allocate();
        
        try {
            
            docBuilder.setEntityResolver(new EntityResolver(configuration));
            docBuilder.setErrorHandler(ErrorHandler.INSTANCE);
            
            final org.w3c.dom.Document domDocument = docBuilder.parse(source);
            docBuilder.reset();
            
            return StandardDOMTranslator.translateDocument(domDocument, documentName);
            
        } catch(final SAXException e) {
            
            if(e.getMessage() != null &&
                e.getMessage().contains(SAXPARSEEXCEPTION_BAD_ELEMENT_CONTENT)) {
                throw new ParsingException(
                    SAXPARSEEXCEPTION_BAD_ELEMENT_CONTENT_EXPLANATION, e);
            }
            
            throw new ParsingException("An exception happened during parsing", e);
            
        } catch(IOException e) {
            
            throw new TemplateInputException("Exception parsing document", e);
            
        } catch(Exception e) {
            
            throw new ParsingException("Exception parsing document", e);
            
        } finally {
            
            pool.release(docBuilder);
            
        }
        
    }

    

    public final List<Node> parseFragment(final Configuration configuration, final String fragment) {
        final String wrappedFragment = wrapFragment(fragment);
        final Document document = 
                parseTemplateUsingPool(
                        configuration, 
                        null, // documentName 
                        new InputSource(new StringReader(wrappedFragment)), 
                        getNonValidatingPool());
        return unwrapFragment(document);
    }
    
    
    protected abstract String wrapFragment(final String fragment);
    protected abstract List<Node> unwrapFragment(final Document document);
    
}

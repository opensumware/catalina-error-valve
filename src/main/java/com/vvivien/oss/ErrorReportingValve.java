package com.vvivien.oss;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Contained;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

/**
 * This is an implementation of a catalina Valve to be used for error reporting.
 * This valve can be used to generate a customized view displayed globally 
 * for all errors.
 * 
 * Unlike other valves that use the <valve/> tag, this one is configured
 * in the host definition as  <Host errorReportValve="..."/>.  Therefore, there
 * it cannot be configured like other valves.  Configuration values are read
 * from catalina.properties.  All properties for ErrorReportingValve start
 * with prefix "error.page.".  A third value is added to determine which page to
 * show depending on the error.
 * 
 * Example:
 * error.page.*=./error/errorAll.html
 * error.page.404=./error/error404.html
 * error.page.500=./error/error.500.html
 * 
 * Properties error.page.* is shown when no other error pages are not configured
 * or found.    
 * 
 * Reference:
 * See attribute "errorReportValveClass" in page 
 * http://tomcat.apache.org/tomcat-7.0-doc/config/host.html
 * 
 * Vladimir Vivien
 *
 */
public class ErrorReportingValve extends ValveBase implements Contained, Valve{
    private static final String CONTENT_TYPE = "text/html";
    private static final String CONTENT_CHARSET = "utf-8";
    private static final String INFO = ErrorReportingValve.class.getCanonicalName() +"/1.0";
    private static final String DEFAULT_PAGE_KEY = "error.page.*";
    private static final String DEFAULT_PAGE_PATH = System.getProperty(DEFAULT_PAGE_KEY);
    private static final String KEY_PAGE_FILE = "PAGE_FILE";
    private static final String KEY_PAGE_FILE_LAST_MOD = "PAGE_FILE_LAST_MODIFIED";
    private static final String KEY_PAGE_CONTENT = "PAGE_CONTENT";
    
    private Map<String,String> propsMap;
    private Map<String,Map<String,Object>> pageMap;
    
    public String getInfo() {
        return INFO;
    }

    public ErrorReportingValve(){
        super(true);
        propsMap = new HashMap<String,String>();
        pageMap  = new HashMap<String,Map<String,Object>>();
        loadProperties();
    }
    
    public void invoke(Request req, Response rsp) throws IOException, ServletException {
        
        getNext().invoke(req, rsp);

        if(
            rsp.isCommitted() ||
            isHttpResponseValid(rsp)
        ){
            return;
        }
        
        // was there a Java error generated, if so force 500 internal error
        Throwable throwable = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (req.isAsyncStarted() && rsp.getStatus() < 400 && throwable == null) {
            return;
        }
        
        if(throwable != null) {
             rsp.setError();
             rsp.reset();
             rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        
        rsp.setSuspended(false);
        
        String errorPagePath = selectErrorPagePath(rsp);
        loadErrorPage(errorPagePath);
        renderErrorPage(errorPagePath, rsp);
        
    }

    /**
     * Stores all properties starting with error.page.*
     */
    private void loadProperties() {
        for(String prop : System.getProperties().stringPropertyNames()){
            if(prop.startsWith("error.page.")){
                propsMap.put(prop, System.getProperty(prop));
            }
        }
    }
    
    /**
     * Selects the path of error page based on configured catalina.properties.
     * @param rsp needed to select key based on error code.
     */
    private String selectErrorPagePath(Response rsp){
        int rspStatus = rsp.getStatus();
        String pageKey = DEFAULT_PAGE_KEY;
        if(rspStatus >= 400){
            pageKey = "error.page." + rspStatus;
        }
        
        String pagePath =  propsMap.get(pageKey); 
        return (pagePath != null) ? pagePath : DEFAULT_PAGE_PATH;
    }
    
    /**
     * Ensure that non-200 responses are valid and not errors.
     * @return 
     */
    private boolean isHttpResponseValid (Response rsp){
        boolean OK = true;
        int code = rsp.getStatus();
        if (code < 400 || rsp.getContentWritten() > 0 || !rsp.isError()) return OK;
        
        String message = rsp.getMessage();
        message = message == null ? "" : message;
        
        // OK if no report for message
        String report = sm.getString("http."+code, message);
        if (report == null){
            if(message.length() == 0) return OK;
        }
        
        return !OK;
    }
    
    /**
     * Renders the error page.
     * @param pagePath
     * @param rsp 
     */
    private void renderErrorPage(String pagePath, Response rsp){
        
        Map<String,Object> pageInfo = pageMap.get(pagePath);
        
        StringBuilder html = null;
        if(pageInfo == null || pageInfo.get(KEY_PAGE_CONTENT) == null){
            container.getLogger().error("The global error page is not configured properly. "
                    + "Rendering default error page. ");
            html = new StringBuilder("<html>");
            html
                .append("<bod>")
                .append("<h1>Error</h1>")
                .append("An occured, your request cannot be completed.").append("<hr/>")
                .append("Error configuration incomplete.").append("<br/>")    
                .append("</body>")
                .append("</html>");
        }else{
            html = (StringBuilder) pageInfo.get(KEY_PAGE_CONTENT);
        }
        
        // commit htlm
        rsp.setContentType(CONTENT_TYPE);
        rsp.setCharacterEncoding(CONTENT_CHARSET);
        try {
            rsp.getReporter().write(html.toString());
        } catch (IOException ex) {
            if(getContainer().getLogger().isDebugEnabled()){
                container.getLogger().debug("Unable to generate custom hml page.", ex);
            }
        }
    }
   
    /**
     * Loads the error page lazily.  It loads a copy of previously generated file.
     * If none has been generated previously, it generates it and caches it.
     * @param path path for the error page file.
     */
    private void loadErrorPage(String path) {
        if (path == null) {
            container.getLogger().error("ErrorReportingValve properties are "
                    + "not configured properly.  See conf/catalina.properties.");
            return;
        }
        
        if(shouldPageReload(path)){
            Map<String, Object> pageInfo = new HashMap<String,Object>();
            File pageFile = new File(path);
            if (!pageFile.exists() || !pageFile.isFile()) {
                container.getLogger().error("Unable to find error file "
                        + pageFile.getAbsolutePath()
                        + " for ErrorReportingValve.");
                return;
            }
            
            Long pageLastMod = pageFile.lastModified();
            
            StringBuilder pageContent = new StringBuilder();
            try{
                @SuppressWarnings("resource")
				BufferedReader reader = new BufferedReader(new FileReader(pageFile));
                String line = null;
                while ((line = reader.readLine()) != null){
                    pageContent.append(line);
                }
                
                // store page info
                pageInfo.put(KEY_PAGE_FILE, pageFile);
                pageInfo.put(KEY_PAGE_CONTENT, pageContent);
                pageInfo.put(KEY_PAGE_FILE_LAST_MOD, pageLastMod);
                pageMap.put(path, pageInfo);
                
            }catch(IOException ex){
                container.getLogger().error("Unable to load global error file " + pageFile, ex);
            }
        }
    }
    
    /**
     * Determine whether a page should be regenerated/
     * @param pagePath - path for page.
     * @return true - if page has not been generated or last modified ts not found 
     * or last modifed ts less than current.
     */
    private boolean shouldPageReload(String pagePath){
        File currentFile = new File(pagePath);
        
        Map<String,Object> pageInfo = pageMap.get(pagePath);
        if (pageInfo == null) return true;
        
        File pageFile = (File) pageInfo.get(KEY_PAGE_FILE);
        Long pageFileLastModified = (Long) pageInfo.get(KEY_PAGE_FILE_LAST_MOD);
        
        return (
            pageFile == null || 
            pageFileLastModified == null || 
            pageFileLastModified < currentFile.lastModified()
        );
    }
    
}

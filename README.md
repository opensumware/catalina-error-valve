Catalina-Error-Valve
====================

This is an implementation of the Catalina Valve interface that can be used to render global Tomcat error pages.
This valve can be used to override the default Catalina error page valve set using the `errorReportValveClass` 
attribute of the `<Host/>` element in `server.xml`.  As with the default error report valve class, 
this valve (ErrorReportingValve) will generate error pages whenever there is a container-wide error generated
by a request.  This valve, however, let you use a customized HTML page to be displayed for the error. 


###Configuration
To use this valve, do the followings:
 * Pull code down and build (use Maven).
 * Put the generated catalina-error-valve jar file in Tomcat's lib directory.
 * Edit `server.xml` and add set attribute `errorReportValveClass` for the desired <Host/>.
   * `<Host ... errorReportValveClass="com.vvivien.oss.ErrorReportingValve">`
 * Add properties in `catalina.properties` to point to HTML pages.
 * All properties for the valve start with prefix `"error.page."`.  
   * `error.page.*` - default page to display when no specific pages are configured
   * `error.page.XXX` - Use XXX to specify an HTTP response code (i.e error.page.404).

####Example:

    error.page.*=./error/errorAll.html    # the default error page
    error.page.404=./error/error404.html  # error page for a 404 
    error.page.500=./error/error.500.html # error page for a 500

Each `error.page.` entry points to the path of an HTML page that will be displayed upon error. 
The location of the page can be relative (to catalina's working directory) or absolute.  If none of the error pages
are not found or configured properly, the class will default to an internal simple page (you don't want that one).

### Reference:
See attribute `"errorReportValveClass"` in page 
http://tomcat.apache.org/tomcat-7.0-doc/config/host.html

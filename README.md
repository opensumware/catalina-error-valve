catalina-error-valve
====================

This is an implementation of a catalina Valve that can be configuered to render global error.
This valve can be used to override the default catalina default error page.  You can configure
it to generate error pages regardless of the context that created the error, therefore creating
a global error page.


Unlike other valves that use the <Valve/> tag, this one is configured in the host definition 
as  <Host errorReportValve="..."/>.  Therefore, it cannot be configured like other valves.  
Configuration values for this valved are read from catalina.properties.  All properties for this valved
start with prefix "error.page.".  A third value is added to determine which error page to show depending 
on the error that was generated.

Example:
error.page.*=./error/errorAll.html    # the default that will be shown when no other pages are configured
error.page.404=./error/error404.html  # shown for a 404 
error.page.500=./error/error.500.html # shown for a 500

Each error.page. entry points to the path of an HTML page that will be displayed upon an error. 
The location of the page can be relative (to catalina's working directory) or absolute.  If none of the error pages
are not found or configured properly, the class will default to an internal simple page.

Reference:
See attribute "errorReportValveClass" in page 
http://tomcat.apache.org/tomcat-7.0-doc/config/host.html

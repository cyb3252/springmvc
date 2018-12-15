package com.cyb.servlet;

import com.cyb.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cyb
 * @date 2018/12/2 - 20:07
 * Tomcat启动加载Springmvc的流程
 * <p>
 * Tomcat启动
 * 1、ScanbasePackage：扫描war下的@Controller、@Service注解的类
 * 2、实例化，将扫描到的类通过反射实例化到iocMap中去
 * 3、依赖注入，将存在依赖的bean进行注入
 * 4、UrlMapping：http请求路径与method建立映射关系
 * <p>
 * Tomcat运行阶段
 * 1、发送http请求，调用servlet的doGet/doPost方法
 * 2、找到urlMapping中找到对应的Method方法对象
 * 3、找到method方法对象后，直接调用
 * 4、响应返回结果
 */
@WebServlet(name = "dispatcherServlet", urlPatterns = "/*", loadOnStartup = 1, initParams = {
        @WebInitParam(name = "base-package", value = "com.cyb")
})
public class DispatcherServlet extends HttpServlet {

    //扫描基类的包
    private String basePackage = "";
    //扫描的包下所有的带包路径的全限定名称
    private List<String> packageNames = new ArrayList<>();
    //实例存放的map
    private Map<String, Object> instanceMap = new HashMap<>();
    //bean的名称
    private Map<String, String> nameMap = new HashMap<>();
    //url所对应执行的方法
    private Map<String, Method> urlMethodMap = new HashMap<>();
    //key = method value=类全限定名
    private Map<Method, String> methodStringMap = new HashMap<>();

    @Override
    protected void doGet (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String path = uri.replace(contextPath, "");

        Method method = urlMethodMap.get(path);
        if (method != null) {
            String packageName = methodStringMap.get(method);
            String controllerName = nameMap.get(packageName);
            method.setAccessible(true);
            Object[] args = hand(req, resp, method);
            try {
                Object result = method.invoke(instanceMap.get(controllerName), args);
                resp.setContentType("utf-8");
                PrintWriter writer = resp.getWriter();
                writer.println(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //参数处理
    private static Object[] hand (HttpServletRequest request, HttpServletResponse response, Method method) {
        //拿到当前执行方法有那些参数
        Class<?>[] parameterTypes = method.getParameterTypes();
        //根据参数的个数，new 一个参数的数组，将方法里所有的参数赋值到args来
        Object[] args = new Object[parameterTypes.length];
        int args_i = 0;
        int index = 0;
        for (Class<?> parameterType : parameterTypes) {
            if (ServletRequest.class.isAssignableFrom(parameterType)) {
                args[args_i++] = request;
            }
            if (ServletResponse.class.isAssignableFrom(parameterType)) {
                args[args_i++] = response;
            }

            //从0-3判断有没有requestParam注解，很明显parameterType为0和1的时候不是
            //当为2和3的时为RequestParam，需要解析
            Annotation[] paramAns = method.getParameterAnnotations()[index];
            if (paramAns.length > 0) {
                for (Annotation paramAn : paramAns) {
                    if (RequestParam.class.isAssignableFrom(paramAn.getClass())) {
                        RequestParam rp = (RequestParam) paramAn;
                        //找到注解里的name和age
                        args[args_i++] = request.getParameter(rp.value());
                    }
                }
            }
            index++;
        }
        return args;
    }

    @Override
    public void init (ServletConfig config) throws ServletException {
        //获取包路径
        basePackage = config.getInitParameter("base-package");
        System.out.println(basePackage);
        //扫描包路径下所有类的全限定名
        doScanBasePackage(basePackage);
        //处理类上有controller repository service
        doInstance();
        //bean注入
        doAutowired();
        //获取controller，执行controller的某个方法
        doHandlerUrlMapping();
    }

    private void doHandlerUrlMapping () {
        if (packageNames.size() < 1) {
            return;
        }
        for (String packageName : packageNames) {
            try {
                Class<?> c = Class.forName(packageName);
                if (c.isAnnotationPresent(Controller.class)) {
                    //该类所有的方法
                    Method[] methods = c.getMethods();
                    StringBuilder baseUrl = new StringBuilder();
                    //在检测类上是否标注了requestMapping
                    if (c.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping requestMapping = c.getAnnotation(RequestMapping.class);
                        baseUrl.append(requestMapping.value());
                    }

                    //遍历该类的所有方法，如果方法上有requestmapping注解就拼装成一个url 最后把url和方法放在一个map中
                    for (Method method : methods) {
                        if (method.isAnnotationPresent(RequestMapping.class)) {
                            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                            baseUrl = baseUrl.append(requestMapping.value());

                            urlMethodMap.put(baseUrl.toString(), method);
                            methodStringMap.put(method, packageName);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        System.out.println("===============methodStringMap===============");
        System.out.println(urlMethodMap);
        System.out.println("===============methodStringMap===============");
        System.out.println(methodStringMap);
    }

    /**
     * 注入
     */
    private void doAutowired () {
        for (Map.Entry<String, Object> entry : instanceMap.entrySet()) {
            Object instance = entry.getValue();
            Class<?> clazz = instance.getClass();

            //判断是否是Controller
            if (clazz.isAnnotationPresent(Controller.class)) {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    if (field.isAnnotationPresent(Autowired.class)) {
                        String autowiredName = field.getAnnotation(Autowired.class).value();
                        //设置反射可以为私有字段赋值
                        field.setAccessible(true);
                        try {
                            field.set(entry.getValue(), instanceMap.get(autowiredName));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    } else {
                        continue;
                    }
                }
            } else {
                continue;
                //如果service层也有注入的话，在这里继续判断即可
            }
        }
    }


    /**
     * 实例化
     * <p>
     * packageNames 实例化的类全限定名的集合
     */
    private void doInstance () {
        //没有需要实例化的类直接跳出
        if (packageNames.size() < 1) {
            return;
        }
        for (String packageName : packageNames) {
            try {
                Class<?> c = Class.forName(packageName);
                //类上注解controller
                if (c.isAnnotationPresent(Controller.class)) {
                    //获取注解
                    Controller controller = c.getAnnotation(Controller.class);
                    //获取@Controller 中value的值
                    String controllerName = controller.value();
                    if (controllerName != null && !controllerName.trim().equals("")) {
                        instanceMap.put(controllerName, c.newInstance());
                        nameMap.put(packageName, controllerName);
                        System.out.println("Controller" + controllerName + ",value" + controller.value() == null ? "" : controller.value());
                    }
                } else if (c.isAnnotationPresent(Service.class)) {
                    Service service = c.getAnnotation(Service.class);
                    String serviceName = service.value();
                    if (serviceName != null && !serviceName.trim().equals("")) {
                        instanceMap.put(serviceName, c.newInstance());
                        nameMap.put(packageName, serviceName);
                        System.out.println("Service=" + serviceName + ",value=" + service.value());
                    }
                } else if (c.isAnnotationPresent(Repository.class)) {
                    Repository repository = c.getAnnotation(Repository.class);
                    String repositoryName = repository.value();
                    if (repositoryName != null && !repositoryName.trim().equals("")) {
                        instanceMap.put(repositoryName, c.newInstance());
                        nameMap.put(packageName, repositoryName);
                        System.out.println("Repository" + repositoryName + ",value" + repository.value());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 扫描包
     *
     * @param basePackage 包名
     */
    private void doScanBasePackage (String basePackage) {
        //由于在idea的情况下 这种方式并不能拿到当前项目的路径
        /*URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
        String path = url.getPath();*/
        //模拟得到的路径
        String path = "D:\\IdeaProjects\\springmvc\\src\\main\\java\\" + basePackage.replaceAll("\\.", "/");
        File basePackageFile = new File(path);
        File[] childFile = basePackageFile.listFiles();
        for (File file : childFile) {

            //如果得到的文件是目录的话
            if (file.isDirectory()) {
                //递归处理
                doScanBasePackage(basePackage + "." + file.getName());
            } else if (file.isFile()) {
                packageNames.add(basePackage + "." + file.getName().split("\\.")[0]);
            }
        }
    }
}

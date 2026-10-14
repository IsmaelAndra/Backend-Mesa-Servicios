package com.mesaayuda.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.springframework.http.CacheControl;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.nio.file.Paths;

/**
 * Sirve SOLO los recursos de marca, que van en el subdirectorio publico/.
 *
 * Antes esta clase exponia la carpeta completa de uploads como estatico y por
 * eso /uploads/** estaba en permitAll. Con solo conocer la URL se descargaba
 * cualquier adjunto de cualquier ticket, incluidos PDFs y capturas de otros
 * usuarios.
 *
 * Ahora hay dos zonas separadas y una URL distinta para cada una:
 *
 *   publico/    logo, icono del sistema, fondos del chat, logos de informe y
 *               avatares de Microsoft Graph. Se muestran en el login, antes de
 *               que exista sesion, asi que no pueden exigir autenticacion.
 *               URL: /uploads/publico/...
 *
 *   adjuntos/   archivos subidos por usuarios a un ticket. NUNCA se sirven como
 *               estatico; van por AttachmentController, que exige sesion y
 *               comprueba que el usuario pueda leer ese ticket.
 *
 * Como las URLs de la zona publica ahora llevan el prefijo /uploads/publico/,
 * los registros ya guardados en branding_config y user.photo_url hay que
 * migrarlos; schema.sql y data.sql ya lo hacen.
 */
@Configuration
  public class WebConfig implements WebMvcConfigurer {
  
      @Value("${app.uploads.dir}")
      private String uploadDir;
  
      @Value("${app.proxy.trust-forwarded-headers:false}")
      private boolean confiarEnCabeceras;

      @Autowired
      private AuditoriaAdministrativaInterceptor auditoriaAdministrativa;

    /** Todo cambio por /admin queda en audit_log; ver AuditoriaAdministrativaInterceptor. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditoriaAdministrativa).addPathPatterns("/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(UploadZones.PUBLICO);
        String location = "file:" + base.toString().replace("\\", "/") + "/";
        // Antes salían sin Cache-Control y el navegador volvía a pedir el fondo del
        // login y el logo en cada visita. Las imágenes de marca llevan un uuid en
        // el nombre (un cambio crea otro archivo), así que pueden guardarse un mes.
        // Los avatares conservan su nombre al actualizarse: caché de una hora.
        registry.addResourceHandler("/uploads/" + UploadZones.PUBLICO + "/avatars/**")
                .addResourceLocations(location + "avatars/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
        registry.addResourceHandler("/uploads/" + UploadZones.PUBLICO + "/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
      }
  
      /**
       * Reescribe la petición para que getRemoteAddr() devuelva el cliente real.
       *
       * <p>Solo se registra si hay proxy declarado. Ponerlo siempre sería un
       * error: sin un proxy delante, esta cabecera es texto que elige el que llama,
       * y aceptarla permitiríafalsear la IP tanto en los límites por origen como
       * en lo que se guarda en audit_log.
       *
       * <p>Va con highestPrecedence para ejecutarse antes que los filtros de la
       * aplicación: si ClientIpFilter corriera primero, leería la IP del proxy.
       */
      @Bean
      public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
          FilterRegistrationBean<ForwardedHeaderFilter> registro =
                  new FilterRegistrationBean<>(new ForwardedHeaderFilter());
          registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
          registro.addUrlPatterns("/*");
          if (!confiarEnCabeceras) {
              registro.setEnabled(false);
          }
          return registro;
      }
  }
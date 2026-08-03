package com.ashenox.starter.log.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Around("execution(* com.ecommerce.template.Service.Interface..*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("✅ {}.{} ejecutado en {}ms", className, methodName, duration);
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ {}.{} falló después de {}ms - Error: {}", 
                     className, methodName, duration, e.getMessage());
            throw e;
        }
    }

    @Around("execution(* com.ecommerce.template.Controller..*(..))")
    public Object logControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        log.info("🌐 Controller: {}.{} - Args: {}", 
                joinPoint.getTarget().getClass().getSimpleName(), 
                methodName, 
                args.length);
        
        return joinPoint.proceed();
    }
}
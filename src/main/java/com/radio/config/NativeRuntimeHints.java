package com.radio.config;

import com.radio.model.StationList;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(NativeRuntimeHints.NativeRuntimeHintsRegistrar.class)
public class NativeRuntimeHints {

    static class NativeRuntimeHintsRegistrar implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Kaynak dosyaları (resources) kaydet
            hints.resources().registerPattern("stations.json");

            // Jackson serileştirme/deserileştirme için sınıfları kaydet
            hints.reflection().registerType(StationList.class,
                builder -> builder.withMembers(
                    org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS
                ));

            hints.reflection().registerType(com.radio.model.RadioStation.class,
                builder -> builder.withMembers(
                    org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS
                ));

            hints.reflection().registerType(String[].class,
                builder -> builder.withMembers(
                    org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
                ));
        }
    }
}

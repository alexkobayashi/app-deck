# Regras de R8 do App Deck.
#
# kotlinx.serialization e Retrofit trazem regras próprias (consumer rules),
# então normalmente não é preciso listar os DTOs aqui. O que fica é o mínimo
# para que uma quebra em release seja improvável.

# Mantém os campos anotados com @Serializable — sem isso o R8 pode renomear
# um campo e a desserialização silenciosamente vira null.
-keepclassmembers,allowobfuscation class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Retrofit usa reflexão nas assinaturas das interfaces de API.
-keep,allowobfuscation,allowshrinking interface dev.alexkobayashi.appdeck.data.remote.**

# Mantém as informações genéricas usadas por Retrofit e kotlinx.serialization.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

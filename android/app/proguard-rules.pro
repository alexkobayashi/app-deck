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

# ML Kit: nenhuma regra própria, de propósito.
#
# A biblioteca já traz regras de consumidor corretas. Acrescentar
# "-keep class com.google.mlkit.vision.barcode.** { *; }" por precaução
# **quebrou** o app: manter essas classes sem ofuscação enquanto as
# dependências delas eram renomeadas rompeu o registro de componentes do ML
# Kit, que casa dependências por identidade de classe. O sintoma era um crash
# no arranque, só em release:
#
#   Unable to get provider com.google.mlkit.common.internal.MlKitInitProvider
#   Caused by: Unsatisfied dependency for component ...
#
# Regra de keep não é gratuita: ela pode desalinhar uma biblioteca cujas
# próprias regras já estavam certas.

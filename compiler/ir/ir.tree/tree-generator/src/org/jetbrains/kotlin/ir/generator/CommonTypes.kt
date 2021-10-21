/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.generator

import org.jetbrains.kotlin.ir.generator.Packages.declarations
import org.jetbrains.kotlin.ir.generator.Packages.exprs
import org.jetbrains.kotlin.ir.generator.Packages.symbols
import org.jetbrains.kotlin.ir.generator.Packages.types
import org.jetbrains.kotlin.ir.generator.Packages.visitors
import org.jetbrains.kotlin.ir.generator.util.type

object Packages {
    val tree = "org.jetbrains.kotlin.ir"
    val exprs = "org.jetbrains.kotlin.ir.expressions"
    val symbols = "org.jetbrains.kotlin.ir.symbols"
    val declarations = "org.jetbrains.kotlin.ir.declarations"
    val types = "org.jetbrains.kotlin.ir.types"
    val visitors = "org.jetbrains.kotlin.ir.visitors"
    val irDescriptors = "org.jetbrains.kotlin.ir.descriptors"
    val descriptors = "org.jetbrains.kotlin.descriptors"
}

val statementOriginType = type(exprs, "IrStatementOrigin")
val elementVisitorType = type(visitors, "IrElementVisitor")
val elementTransformerType = type(visitors, "IrElementTransformer")
val mutableAnnotationContainerType = type(declarations, "IrMutableAnnotationContainer")
val irTypeType = type(types, "IrType")

val symbolType = type(symbols, "IrSymbol")
val bindableSymbolType = type(symbols, "IrBindableSymbol")
val packageFragmentSymbolType = type(symbols, "IrPackageFragmentSymbol")
val fileSymbolType = type(symbols, "IrFileSymbol")
val externalPackageFragmentSymbolType = type(symbols, "IrExternalPackageFragmentSymbol")
val anonymousInitializerSymbolType = type(symbols, "IrAnonymousInitializerSymbol")
val enumEntrySymbolType = type(symbols, "IrEnumEntrySymbol")
val fieldSymbolType = type(symbols, "IrFieldSymbol")
val classifierSymbolType = type(symbols, "IrClassifierSymbol")
val classSymbolType = type(symbols, "IrClassSymbol")
val scriptSymbolType = type(symbols, "IrScriptSymbol")
val typeParameterSymbolType = type(symbols, "IrTypeParameterSymbol")
val valueSymbolType = type(symbols, "IrValueSymbol")
val valueParameterSymbolType = type(symbols, "IrValueParameterSymbol")
val variableSymbolType = type(symbols, "IrVariableSymbol")
val returnTargetSymbolType = type(symbols, "IrReturnTargetSymbol")
val functionSymbolType = type(symbols, "IrFunctionSymbol")
val constructorSymbolType = type(symbols, "IrConstructorSymbol")
val simpleFunctionSymbolType = type(symbols, "IrSimpleFunctionSymbol")
val returnableBlockSymbolType = type(symbols, "IrReturnableBlockSymbol")
val propertySymbolType = type(symbols, "IrPropertySymbol")
val localDelegatedPropertySymbolType = type(symbols, "IrLocalDelegatedPropertySymbol")
val typeAliasSymbolType = type(symbols, "IrTypeAliasSymbol")

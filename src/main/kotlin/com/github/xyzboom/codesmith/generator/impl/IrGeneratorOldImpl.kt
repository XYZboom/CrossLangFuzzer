package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.checkers.IAccessChecker
import com.github.xyzboom.codesmith.checkers.ISignatureChecker
import com.github.xyzboom.codesmith.checkers.impl.AccessCheckerImpl
import com.github.xyzboom.codesmith.checkers.impl.SignatureCheckerImpl
import com.github.xyzboom.codesmith.generator.*
import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.IrAccessModifier.*
import com.github.xyzboom.codesmith.irOld.IrElement
import com.github.xyzboom.codesmith.irOld.declarations.*
import com.github.xyzboom.codesmith.irOld.declarations.builtin.AbstractBuiltinClass
import com.github.xyzboom.codesmith.irOld.declarations.builtin.AnyClass
import com.github.xyzboom.codesmith.irOld.declarations.impl.IrConstructorImpl
import com.github.xyzboom.codesmith.irOld.declarations.impl.IrFunctionImpl
import com.github.xyzboom.codesmith.irOld.declarations.impl.IrValueParameterImpl
import com.github.xyzboom.codesmith.irOld.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrFunctionCallExpression
import com.github.xyzboom.codesmith.irOld.expressions.impl.*
import com.github.xyzboom.codesmith.irOld.types.*
import com.github.xyzboom.codesmith.irOld.types.IrClassType.*
import com.github.xyzboom.codesmith.irOld.types.builtin.IrBuiltinTypes
import kotlin.math.roundToInt
import kotlin.random.Random

class IrGeneratorOldImpl(
    private val random: Random = Random.Default,
    private val config: GeneratorConfigOld = GeneratorConfigOld.default
): IrGeneratorOld, IAccessChecker by AccessCheckerImpl(), ISignatureChecker by SignatureCheckerImpl() {
    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    private val generatedClasses = ArrayList<IrClass>(64)
    private val subclassesCache = HashMap<IrClass, List<IrClass>>(64)

    @CodeSmithDsl
    override fun IrClass.constructor(
        superCall: IrConstructorCallExpression,
        accessModifier: IrAccessModifier,
        valueParameters: MutableList<IrValueParameter>,
        constructorCtx: IrConstructor.() -> Unit
    ): IrConstructor? {
        return IrConstructorImpl(accessModifier, this, superCall, valueParameters)
            .apply(constructorCtx).apply {
                if (!this@constructor.containsSameSignature(this)) {
                    this@constructor.functions.add(this)
                } else return null
            }
    }

    @CodeSmithDsl
    override fun IrFunctionContainer.function(
        name: String,
        accessModifier: IrAccessModifier,
        valueParameters: MutableList<IrValueParameter>,
        returnType: IrConcreteType,
        functionCtx: IrFunction.() -> Unit
    ): IrFunction? {
        return IrFunctionImpl(name, this, accessModifier, valueParameters, returnType)
            .apply(functionCtx).apply {
                if (!this@function.containsSameSignature(this)) {
                    this@function.functions.add(this)
                } else return null
            }
    }

    //<editor-fold desc="subtyping">
    private val IrClass.subclasses: List<IrClass>
        get() {
            if (subclassesCache.containsKey(this)) {
                return subclassesCache[this]!!
            }
            return ArrayList<IrClass>().apply {
                add(this@subclasses)
                for (clazz in generatedClasses) {
                    if (this@subclasses in clazz.allSuperClasses) {
                        add(clazz)
                    }
                }
                subclassesCache[this@subclasses] = this
            }
        }

    //</editor-fold>

    override fun IrElement.generateExpressionFor(clazz: IrClass): IrExpression {
        if (clazz is AbstractBuiltinClass) {
            return clazz.generateValueArgumentFor(random, clazz)
        }
        if (!isAccessible(clazz)) {
            return IrTodoExpressionImpl()
        }
        if (clazz.classType == ABSTRACT || clazz.classType == INTERFACE) {
            return IrAnonymousObjectImpl(clazz)
        }
        return IrSpecialConstructorCallExpressionImpl(
            clazz.specialConstructor ?: throw IllegalStateException()
        )
    }

    override fun randomName(startsWithUpper: Boolean): String {
        val length = config.nameLengthRange.random(random)
        val sb = StringBuilder(
            if (startsWithUpper) {
                "${upperLetters.random(random)}"
            } else {
                "${lowerLetters.random(random)}"
            }
        )
        for (i in 1 until length) {
            sb.append(lettersAndNumbers.random(random))
        }
        val result = sb.toString()
        val lowercase = result.lowercase()
        if (generatedNames.contains(lowercase)) {
            return randomName(startsWithUpper)
        }
        generatedNames.add(lowercase)
        return result
    }

    override fun IrProgram.generateModuleDependencies() {
        if (modules.size <= 1) return
        if (modules.size == 2) {
            if (random.nextBoolean()) {
                modules[0].dependsOn(modules[1])
            } else {
                modules[1].dependsOn(modules[0])
            }
        }
        val allModules = ArrayList(modules)
        val needMax = (allModules.size * config.moduleDependencySelectionRate).toInt().coerceAtLeast(2)
        val waitBeDepended = ArrayDeque<IrModule>()
        val iterator = allModules.iterator()
        while (iterator.hasNext()) {
            for (i in 0 until needMax) {
                val next = iterator.next()
                if (waitBeDepended.isNotEmpty()) {
                    waitBeDepended.shuffle(random)
                    val minTake = (waitBeDepended.size * config.moduleDependencyMinRate)
                        .roundToInt().coerceIn(0..<waitBeDepended.size)
                    val maxTake = (waitBeDepended.size * config.moduleDependencyMaxRate)
                        .roundToInt().coerceIn(minTake + 1..waitBeDepended.size)
                    next.dependsOn(waitBeDepended.take(random.nextInt(minTake, maxTake)))
                }
                waitBeDepended.add(next)
                if (!iterator.hasNext()) {
                    break
                }
            }
            val sizeToMove = (waitBeDepended.size * config.moduleDependencyEliminateRate).toInt()
            for (i in 0 until sizeToMove) {
                waitBeDepended.removeFirstOrNull()
            }
        }
    }

    override fun generateClassInheritance() {
        for (clazz in generatedClasses) {
            if (clazz.classType == INTERFACE) continue
            if (random.nextFloat() > config.classHasSuperProbability) {
                clazz.superType = IrBuiltinTypes.ANY
                continue
            }
            val accessibleClasses = clazz.containingFile.accessibleClasses
            val accessibleOpenClass = accessibleClasses.filter {
                it !== AnyClass && it !== clazz
                        && it.accessModifier <= clazz.accessModifier
                        && (it.classType == ABSTRACT || it.classType == OPEN)
                        && clazz !in it.allSuperClasses
            }
            if (accessibleOpenClass.isEmpty()) {
                clazz.superType = IrBuiltinTypes.ANY
                continue
            }
            val superType = accessibleOpenClass.random(random)
            clazz.superType = superType.type
        }
    }

    override fun topologyVisit(visitor: IrClass.() -> Unit) {
        val visited = mutableSetOf<IrClass>()
        val visiting = ArrayDeque<IrClass>()
        for (clazz in generatedClasses) {
            if (clazz.classType == INTERFACE) continue
            if (clazz.superType == IrBuiltinTypes.ANY) {
                visiting.add(clazz)
            }
        }
        while (visiting.isNotEmpty()) {
            val now = visiting.removeFirst()
            if (now in visited) continue
            now.visitor()
            for (subclass in generatedClasses) {
                if (subclass.superType?.declaration === now) {
                    visiting.add(subclass)
                }
            }
            visited.add(now)
        }
    }

    override fun generate(): IrProgram {
        val prog = program {
            for (i in 0 until config.moduleNumRange.random(random)) {
                module()
            }
            generateModuleDependencies()
            for (module in modules) {
                with(module) {
                    var lastPackage: IrPackage? = null
                    for (i in 0 until config.packageNumRange.random(random)) {
                        lastPackage = if (random.nextBoolean()) {
                            `package` {
                                generateFiles()
                            }
                        } else {
                            `package`(parent = lastPackage) {
                                generateFiles()
                            }
                        }
                    }
                }
            }
            mainModule = module {
                dependsOn(modules)
            }
        }
        generateClassInheritance()
        topologyVisit {
            generateConstructors(config.constructorNumRange.random(random))
            generateConstructors(1, PUBLIC) // generate at least 1 public constructor
        }
        // ensure at all no arg constructors are public
        for (clazz in generatedClasses) {
            val noArg = clazz.declarations.filterIsInstance<IrConstructor>().filter { it.valueParameters.isEmpty() }
            noArg.forEach { it.accessModifier = PUBLIC }
        }
        for (clazz in generatedClasses) {
            if (clazz.classType == INTERFACE) continue
            for (i in 0 until config.functionParameterNumRange.random(random)) {
                var generateFunction = clazz.generateFunction()
                while (generateFunction == null) {
                    generateFunction = clazz.generateFunction()
                }
            }
        }
        for (clazz in generatedClasses) {
            for (function in clazz.functions) {
                if (function !is IrConstructor) {
                    function.generateExpressions()
                }
            }
        }
        return prog
    }

    override fun IrPackage.generateFiles() {
        for (i in 0 until config.fileNumRange.random(random)) {
            file(fileType = IrFileType.entries.random(random)) { generateClasses() }
        }
    }

    override fun IrFile.generateClasses() {
        for (i in 0 until config.classNumRange.random(random)) {
            val chooseType = IrClassType.entries.random(random)
            val chooseModifier = listOf(PUBLIC, INTERNAL).random(random)
            val accessibleClasses = accessibleClasses
            val interfaces = accessibleClasses.filter {
                it.accessModifier <= chooseModifier && it.classType == INTERFACE
            }.shuffled(random)
            val implements = if (interfaces.isNotEmpty()) {
                interfaces.take(
                    config.classImplNumRange.random(random)
                        .coerceAtMost(interfaces.size - 1)
                ).map { it.type }
            } else {
                emptyList()
            }
            `class`(
                containingFile = this, classType = chooseType, accessModifier = chooseModifier,
                implementedTypes = implements
            ) {
                generatedClasses.add(this)
            }
        }
    }

    override fun IrClass.generateConstructors(num: Int, accessModifier: IrAccessModifier?): List<IrConstructor> {
        if (this.classType == INTERFACE) {
            throw IllegalStateException("An interface should not have constructor")
        }
        val superType = (superType
            ?: throw IllegalStateException("A class without super type should not call super constructors"))
        val superTypePackage = superType.declaration.containingPackage
        val samePackage = superTypePackage == this.containingPackage
        var accessibleSuperConstructors = superType.declaration.functions.filterIsInstance<IrConstructor>()
            .filter {
                it.accessModifier == PUBLIC || it.accessModifier == PROTECTED ||
                        (samePackage && it.accessModifier == INTERNAL)
            }
        if (accessibleSuperConstructors.isEmpty()) {
            accessibleSuperConstructors = superType.declaration.generateConstructors(1, PUBLIC)
        }
        val accessibleClasses = when (val containingDeclaration = containingDeclaration) {
            is IrClass -> TODO()
            is IrFile -> containingDeclaration.accessibleClasses
            else -> throw IllegalStateException()
        }
        val result = mutableListOf<IrConstructor>()
        for (i in 0 until num) {
            val superConstructor = accessibleSuperConstructors.random(random)
            var generated = generateConstructor(superConstructor, accessModifier)
            while (generated == null) {
                generated = generateConstructor(superConstructor, accessModifier)
            }
            result.add(generated)
        }
        return result
    }

    override fun IrClass.generateConstructor(
        superConstructor: IrConstructor, accessModifier: IrAccessModifier?
    ): IrConstructor? {
        val superValueParameters = superConstructor.valueParameters
        val superValueArguments = superValueParameters.map { generateExpressionFor(it.type.declaration) }
        val chooseModifier = accessModifier ?: PUBLIC
        val valueParameters = mutableListOf<IrValueParameter>()
        return constructor(
            IrConstructorCallExpressionImpl(superConstructor, superValueArguments), chooseModifier,
            valueParameters = valueParameters
        ) {
            for (p in 0 until config.constructorParameterNumRange.random(random)) {
                valueParameters.add(randomValueParameter())
            }
        }
    }

    override fun IrClass.generateFunction(): IrFunction? {
        val chooseAccessModifier = PUBLIC
        val accessibleClasses = containingFile.accessibleClasses.filter {
            it.accessModifier <= chooseAccessModifier
        }
        val chooseReturnType = accessibleClasses.random(random)
        return function(accessModifier = chooseAccessModifier, returnType = chooseReturnType.type) {
            expressions.add(this@generateFunction.generateExpressionFor(chooseReturnType))
            for (p in 0 until config.functionParameterNumRange.random(random)) {
                valueParameters.add(randomValueParameter())
            }
        }
    }

    override fun IrFunction.generateExpressions() {
        val generators = listOf(::generateFunctionCallExpr)
        for (i in 0 until config.functionExpressionNumRange.random(random)) {
            val chooseGenerator = generators.random(random)
            val expression = chooseGenerator.invoke(this)
            expressions.add(0, expression)
        }
    }

    override fun generateFunctionCallExpr(function: IrFunction): IrFunctionCallExpression {
        with(function) {
            val containingClassOrFile = containingClass ?: containingFile
            val classes = containingFile.accessibleClasses.filter { clazz ->
                clazz.functions.any { func -> func !is IrConstructor && containingClassOrFile.isAccessible(func) }
            }
            if (classes.isEmpty()) {
                return IrConstructorCallExpressionImpl(AnyClass.constructor, emptyList())
            }
            val chooseClass = classes.random(random)
            val functions = chooseClass.functions.filter { func ->
                func !is IrConstructor && containingClassOrFile.isAccessible(func)
            }
            val chooseFunction = functions.random(random)
            val receiver = containingClassOrFile.generateExpressionFor(chooseClass)
            val args =
                chooseFunction.valueParameters.map { containingClassOrFile.generateExpressionFor(it.type.declaration) }
            return IrFunctionCallExpressionImpl(receiver, chooseFunction, args)
        }
    }

    override fun IrFunction.randomValueParameter(): IrValueParameter {
        val file = containingFile
        val chooseNullability = when (file.fileType) {
            IrFileType.KOTLIN -> if (random.nextBoolean()) Nullability.NOT_NULL else Nullability.NULLABLE
            IrFileType.JAVA -> Nullability.entries.random(random)
        }
        val chooseClass = file.accessibleClasses.filter {
            (this.containingClass == null || it !in this.containingClass!!.subclasses) &&
                    it.accessModifier <= this.accessModifier &&
                    (it.subclasses.size > 1 ||
                            (it.subclasses.size == 1 && it.classType != INTERFACE && it.classType != ABSTRACT))
        }.random(random)
        val chooseType = chooseClass.type.copy(chooseNullability)
        return IrValueParameterImpl(randomName(false), chooseType)
    }
}
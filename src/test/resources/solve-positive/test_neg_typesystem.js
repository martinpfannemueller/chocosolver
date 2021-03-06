scope({c0_Measurable:2, c0_performance:2});
defaultScope(1);
intRange(-8, 7);
stringLength(16);

c0_Measurable = Abstract("c0_Measurable");
c0_performance = c0_Measurable.addChild("c0_performance").withCard(1, 1);
c0_D = Clafer("c0_D").withCard(1, 1).extending(c0_Measurable);
c0_C = Clafer("c0_C").withCard(1, 1).extending(c0_Measurable);
c0_performance.refTo(Int);
Constraint(equal(joinRef(join(global(c0_C), c0_performance)), minus(joinRef(join(global(c0_D), c0_performance)))));

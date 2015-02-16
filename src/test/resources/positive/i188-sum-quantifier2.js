scope({c0_Component:3, c0_energy:3});
defaultScope(1);
intRange(-8, 7);
stringLength(16);

c0_Component = Abstract("c0_Component");
c0_energy = c0_Component.addChild("c0_energy").withCard(1, 1);
c0_c1 = Clafer("c0_c1").withCard(1, 1).extending(c0_Component);
c0_c2 = Clafer("c0_c2").withCard(1, 1).extending(c0_Component);
c0_c3 = Clafer("c0_c3").withCard(1, 1).extending(c0_Component);
c0_total = Clafer("c0_total").withCard(1, 1);
c0_energy.refTo(Int);
c0_total.refTo(Int);
Constraint(implies(some(global(c0_total)), equal(joinRef(global(c0_total)), sum(join(global(c0_Component), c0_energy)))));
c0_c1.addConstraint(equal(joinRef(join($this(), c0_energy)), constant(1)));
c0_c2.addConstraint(equal(joinRef(join($this(), c0_energy)), constant(1)));
c0_c3.addConstraint(equal(joinRef(join($this(), c0_energy)), constant(1)));
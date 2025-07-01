from matplotlib import projections
from pysat.solvers import Solver
from pysat.card import ITotalizer
from pysat.formula import IDPool

import signal
import math
import argparse
import os
import random

### Gebruik:
# Stap 1: pip3 install python-sat
# Stap 2: python3 decompose_fsm.py -h

keep_log = False
record_file = './results/log.txt' if keep_log else None

filename = None


def main():
  parser = argparse.ArgumentParser(description='Decomposes a FSM into smaller components by remapping its outputs. Uses a SAT solver.')
  parser.add_argument('-c', '--components', type=int, default=2, help='number of components')
  parser.add_argument('-w', '--weak', default=False, action='store_true', help='look for weak decomposition')
  parser.add_argument('--add-state-trans', default=False, action='store_true', help='adds state transitivity constraints')
  parser.add_argument('-t', '--timeout', type=int, default=None, help='timeout (in seconds)')
  parser.add_argument('-s', '--save', default=False, action="store_true", help='saves results to file')
  parser.add_argument('filename', help='path to .dot file')
  args = parser.parse_args()

  global filename
  filename = args.filename
  if(os.path.isdir(filename)):
    dirname = filename
    directory = os.fsencode(filename)
    for file in os.listdir(directory):
      filename = os.fsdecode(file)
      print(filename)
      if filename.endswith(".dot"):
        with open(dirname + os.sep + filename) as file:
          machine = parse_dot_file(file)
        best = len(machine.states)
        for c in range(2, 5):
          bound, cur_projections, cur_rids = decompose(args, machine, c)
          if max(bound.values()) >= best:
            print("No improvement")
            c -= 1
            break
          best = max(bound.values())
          projections = cur_projections
          rids = cur_rids
        print("bound found: " + str(best) + " with " + str(c) + " components")
        if args.save:
          with open(dirname + os.sep + filename[:-3] + "txt", 'w') as f:
            if best < len(machine.states):
              f.write(str(c) + "\n")
              for o in machine.outputs:
                f.write(str(o))
                for rid in rids:
                  f.write(" " + str(projections[rid][o].split("_")[2]))
                f.write("\n")
            else: # No decomposition possible, use identity map instead
              f.write("1\n")
              i = 0
              for o in machine.outputs:
                f.write(str(o) + " " + str(i) + "\n")
                i += 1
          print("Results saved")
  else:
    # Aantal componenten. c = 1 is zinloos, maar zou moeten werken
    c = args.components
    assert c >= 1
    if filename.isdigit():
      machine = integer_machine(int(filename))
      decompose(args, machine, c)
    else:
      with open(filename) as file:
        machine = parse_dot_file(file)
      bound, projections, rids = decompose(args, machine, c)

def integer_machine(N):
  initial = 1
  states = [i for i in range(N)]
  inputs = ['a']
  outputs = [i for i in range(N)]
  transition_map = {(i, 'a'): (i + 1) % N for i in range(N)}
  output_map = {(i, 'a'): i for i in range(N)}
  return FSM(initial, states, inputs, outputs, transition_map, output_map)

def decompose(args, machine, c):
  # Als er maar 1 state is, valt er niks te ontbinden, maar het script zou niet
  # moeten crashen. Het aantal outputs moet minstens 3 zijn voor een zinvolle
  # decompositie.
  assert len(machine.states) >= 1
  assert len(machine.inputs) >= 1
  assert len(machine.outputs) >= 1

  print(f'Input FSM: {len(machine.states)} states, {len(machine.inputs)} inputs, and {len(machine.outputs)} outputs')

  if args.timeout:

    def timeout_handler(*_):
      with open(record_file, 'a') as file:
        last_two_comps = '/'.join(filename.split('/')[-2:])
        file.write(f'{last_two_comps}\t{len(machine.states)}\t{len(machine.inputs)}\t{len(machine.outputs)}\t{args.weak}\t{c}\tTIMEOUT\n')
      print('TIMEOUT')
      exit()

    signal.signal(signal.SIGALRM, timeout_handler)
    signal.alarm(args.timeout)
  with Encoder(machine, c, args.weak, add_state_trans=args.add_state_trans, record_file=record_file) as encoder:
    encoder.solve()
    bound  = encoder.bound
    projections = encoder.projections
    rids = encoder.rids
  return bound, projections, rids

###################################
# .dot file parser (heuristic)
class FSM:
  def __init__(self, initial_state, states, inputs, outputs, transition_map, output_map):
    self.initial_state = initial_state
    self.states = states
    self.inputs = inputs
    self.outputs = outputs
    self.transition_map = transition_map
    self.output_map = output_map

  def __str__(self):
    return f'FSM({self.initial_state}, {self.states}, {self.inputs}, {self.outputs}, {self.transition_map}, {self.output_map})'

  def transition(self, s, a):
    return self.transition_map[(s, a)]

  def output(self, s, a):
    return self.output_map[(s, a)]


def parse_dot_file(lines):
  def parse_transition(line):
    (l, _, r) = line.partition('->')
    s = l.strip()

    (l, _, r) = r.partition('[label="')
    t = l.strip()

    (l, _, _) = r.partition('"]')
    (i, _, o) = l.partition('/')

    return (s, i, o, t)

  initial_state = None
  states, inputs, outputs = set(), set(), set()
  transition_map, output_map = {}, {}

  for line in lines:
    (s, i, o, t) = parse_transition(line)
    if s and i and o and t:
      states.add(s)
      inputs.add(i)
      outputs.add(o)
      states.add(t)

      transition_map[(s, i)] = t
      output_map[(s, i)] = o

      if not initial_state:
        initial_state = s

  assert initial_state in states
  assert len(transition_map) == len(states) * len(inputs)
  assert len(output_map) == len(states) * len(inputs)

  return FSM(initial_state, states, inputs, outputs, transition_map, output_map)

def random_fsm(size, inputs, outputs):
  initial_state = 0
  states = list(range(size))
  transition_map = {}
  output_map = {}
  for state in states:
    for input in inputs:
      transition_map[(state, input)] = random.choice(states)
      output_map[(state, input)] = random.choice(outputs)
  return FSM(initial_state, states, inputs, outputs, transition_map, output_map)

def product_fsm(fsm1, fsm2):
  assert fsm1.inputs == fsm2.inputs
  initial_state = (fsm1.initial_state, fsm2.initial_state)
  states = [(x, y) for x in fsm1.states for y in fsm2.states]
  transition_map = {}
  output_map = {}
  inputs = fsm1.inputs
  outputs = [(x, y) for x in fsm1.outputs for y in fsm2.outputs]
  for state in states:
    for input in inputs:
      transition_map[(state, input)] = (fsm1.transition(state[0], input), fsm2.transition(state[1], input))
      output_map[(state, input)] = (fsm1.output(state[0], input), fsm2.transition(state[1], input))
  return FSM(initial_state, states, inputs, outputs, transition_map, output_map)

###################################
# Utility functions
def print_table(cell, rs, cs):
  first_col_size = max([len(str(r)) for r in rs])
  col_size = 1 + max([len(str(c)) for c in cs] + [len(cell(r, c)) for c in cs for r in rs])

  print(''.rjust(first_col_size), end='')
  for c in cs:
    print(str(c).rjust(col_size), end='')
  print('')

  for r in rs:
    print(str(r).rjust(first_col_size), end='')
    for c in cs:
      print(cell(r, c).rjust(col_size), end='')
    print('')


class Progress:
  def __init__(self, name: str, guess: int):
    self.reset(name, guess, show=False)

  def reset(self, name: str, guess: int, show: bool = True):
    self.name = name
    self.guess = math.ceil(guess)
    self.count = 0
    self.percentage = None

    if show:
      print(name)

  def add(self, n: int = 1):
    self.count += n
    percentage = math.floor(100 * self.count / self.guess)

    if percentage != self.percentage:
      self.percentage = percentage
      print(f'{self.percentage}%', end='', flush=True)
      print('\r', end='')


###################################
# Main logic
class Encoder:
  def __init__(self, machine: FSM, components: int = 2, weak: bool = False, add_state_trans: bool = False, record_file: str = None, progress: Progress = None):
    self.machine = machine
    self.c = components
    self.weak = weak
    self.record_file = record_file
    self.progress = progress if progress else Progress('', 1)

    # optionally add state transitivity constraints. This is not necessary for
    # the decomposition and it is cubic in the number of states, so it's off
    # by default.
    self.add_state_trans = add_state_trans

    self.N = len(self.machine.states)
    self.rids = [i for i in range(self.c)]
    self.vpool = IDPool()
    self.solver = Solver()

  def __enter__(self):
    self.solver = Solver()
    self.solver.__enter__()

    self.encode()

    if self.weak:
      self.encode_weak_size_constraints()
    else:
      self.encode_strict_size_constraints()
    assert hasattr(self, 'rhs')

    return self

  def __exit__(self, *args):
    return self.solver.__exit__(*args)

  def add_clause(self, cls, no_return=True):
    self.solver.add_clause(cls, no_return)

  def add_clauses(self, clss, no_return=True):
    self.solver.append_formula(clss, no_return)

  # Een hulp variabele voor False en True, maakt de andere variabelen eenvoudiger
  def var_const(self, b) -> int:
    return self.vpool.id(('const', b))

  # Een variabele die aangeeft of x en y gerelateerd zijn. Deze variabele is
  # symmetrisch en reflexief. De id is een object die de relatie identificeert.
  # Zo kunnen we meerdere relaties encoderen.
  def var_rel_abs(self, id, x, y) -> int:
    if x == y:
      return self.var_const(True)

    [sx, sy] = sorted([x, y])
    return self.vpool.id(('r', id, sx, sy))

  # Een relatie op de output-elementen.
  def var_rel(self, rid, o1, o2) -> int:
    return self.var_rel_abs(('output', rid), o1, o2)

  # De relatie op outputs geeft een relaties op states. Deze relatie moet ook een
  # bisimulatie zijn.
  def var_state_rel(self, rid, s1, s2) -> int:
    return self.var_rel_abs(('state', rid), s1, s2)

  # Voor elke relatie, en elke equivalentie-klasse, kiezen we precies 1 state
  # als representant. Deze variabele geeft aan welk element.
  def var_state_rep(self, rid, s) -> int:
    return self.vpool.id(('state_rep', rid, s))

  def encode(self):
    # lokale variabelen om het wat leesbaarder te maken
    rids, os, states, inputs = self.rids, list(self.machine.outputs), list(self.machine.states), list(self.machine.inputs)

    print('===============')
    print('Start encoding')
    self.add_clause([self.var_const(True)])
    self.add_clause([-self.var_const(False)])

    # Contraints zodat de relatie een equivalentie relatie is. We hoeven alleen
    # maar transitiviteit te encoderen, want refl en symm zijn ingebouwd in de var.
    self.progress.reset('transitivity (o)', guess=len(rids) * len(os) ** 3)
    for rid in rids:
      for xo in os:
        for yo in os:
          for zo in os:
            # als xo R yo en yo R zo dan xo R zo
            self.add_clause([-self.var_rel(rid, xo, yo), -self.var_rel(rid, yo, zo), self.var_rel(rid, xo, zo)])
            self.progress.add()

    if self.add_state_trans:
      self.progress.reset('transitivity (s)', guess=len(rids) * len(states) ** 3)
      for rid in rids:
        for sx in states:
          for sy in states:
            for sz in states:
              # als sx R sy en sy R sz dan sx R sz
              self.add_clause([-self.var_state_rel(rid, sx, sy), -self.var_state_rel(rid, sy, sz), self.var_state_rel(rid, sx, sz)])
              self.progress.add()

    # Constraint zodat de relaties samen alle elementen kunnen onderscheiden.
    # (Aka: the bijbehorende quotienten zijn joint-injective.)
    self.progress.reset('injectivity', guess=len(os) * (len(os) - 1) / 2)
    for xi, xo in enumerate(os):
      for yo in os[xi + 1 :]:
        # Tenminste een rid moet een verschil maken
        self.add_clause([-self.var_rel(rid, xo, yo) for rid in self.rids])
        self.progress.add()

    # sx ~ sy => for each input: (1) outputs equivalent AND (2) successors related
    # Momenteel hebben we niet de inverse implicatie, is misschien ook niet nodig?
    self.progress.reset('bisimulation modulo rel', guess=len(rids) * len(states) * len(states) * len(inputs))
    for rid in rids:
      for sx in states:
        for sy in states:
          for i in inputs:
            # sx ~ sy => output(sx, i) ~ output(sy, i)
            ox = self.machine.output(sx, i)
            oy = self.machine.output(sy, i)
            self.add_clause([-self.var_state_rel(rid, sx, sy), self.var_rel(rid, ox, oy)])

            # sx ~ sy => delta(sx, i) ~ delta(sy, i)
            tx = self.machine.transition(sx, i)
            ty = self.machine.transition(sy, i)
            self.add_clause([-self.var_state_rel(rid, sx, sy), self.var_state_rel(rid, tx, ty)])

            self.progress.add()

    # De constraints die zorgen dat representanten ook echt representanten zijn.
    self.progress.reset('representatives', guess=len(rids) * len(states))
    for rid in rids:
      for ix, sx in enumerate(states):
        # Belangrijkste: een element is een representant, of equivalent met een
        # eerder element. We forceren hiermee dat de solver representanten moet
        # kiezen (voor aan de lijst).
        self.add_clause([self.var_state_rep(rid, sx)] + [self.var_state_rel(rid, sx, sy) for sy in states[:ix]])

        for sy in states[:ix]:
          # rx en ry kunnen niet beide een representant zijn, tenzij ze
          # niet gerelateerd zijn.
          self.add_clause([-self.var_state_rep(rid, sx), -self.var_state_rep(rid, sy), -self.var_state_rel(rid, sx, sy)])

        self.progress.add()

    # Op dit punt is de encodering klaar, op de constraints voor de grootte na.
    # Dit hangt af van de weak of strict decompositie.

  def encode_weak_size_constraints(self):
    self.rhs = []
    self.lower_bound = int(math.floor((self.N - 1) ** (1 / self.c)))
    self.upper_bound = int(self.N)
    print(f'weak size constraints {self.lower_bound} {self.upper_bound}')
    # In de weak decompositie, minimaliseren we de grootte van elk component.
    # Dus voor elk component voegen we een cardinality constraint toe. We
    # gebruiken ITotalizer, omdat deze incrementeel is.
    for rid in self.rids:
      with ITotalizer([self.var_state_rep(rid, sx) for sx in self.machine.states], ubound=self.upper_bound, top_id=self.vpool.top) as cnf_optim:
        self.vpool.occupy(self.vpool.top + 1, cnf_optim.top_id)
        self.vpool.top = cnf_optim.top_id
        self.add_clauses(cnf_optim.cnf.clauses)
        self.rhs.append(cnf_optim.rhs)

  def encode_strict_size_constraints(self):
    self.lower_bound = int(math.floor(self.c * (self.N - 1) ** (1 / self.c)))
    self.upper_bound = int(self.N + self.c - 1)
    print(f'strict size constraints {self.lower_bound} {self.upper_bound}')
    # In de sterke decompositie, minimaliseren we de som van de componenten.
    # Dit komt neer op het feit dat we k representanten kiezen in de lijst
    # rids * states. We gebruiken ITotalizer, omdat deze incrementeel is.
    with ITotalizer([self.var_state_rep(rid, sx) for rid in self.rids for sx in self.machine.states], ubound=self.upper_bound, top_id=self.vpool.top) as cnf_optim:
      self.add_clauses(cnf_optim.cnf.clauses)
      self.rhs = cnf_optim.rhs

  def optimise_constraints(self):
    if self.weak:
      self.optimise_weak_constraints()
    else:
      self.optimise_strict_constraints()

    print(f'done searching, found bound(s) = {self.bound}')

  def optimise_weak_constraints(self):
    bounds = {}
    smallest = None
    todo = self.rids.copy()

    while todo:
      lower_bound = self.N
      for rid in bounds:
        lower_bound /= bounds[rid]

      lower_bound = int(math.floor((math.ceil(lower_bound) - 1) ** (1.0 / len(todo))))
      upper_bound = smallest if smallest else self.N

      while upper_bound - lower_bound >= 2:
        mid_size = int((lower_bound + upper_bound) / 2)
        print(f'W Trying {lower_bound} < {mid_size} < {upper_bound}', end='', flush=True)
        assumptions = [-self.rhs[rid][b] for (rid, b) in bounds.items() if b < self.N]
        assumptions += [-self.rhs[rid][mid_size] for rid in todo]
        sat = self.solver.solve(assumptions=assumptions)
        if sat:
          print('\tdown')
          upper_bound = mid_size
          continue
        else:
          print('\tup')
          lower_bound = mid_size
          continue

      print(f'Found bound {upper_bound} for {todo[0]}')
      bounds[todo.pop(0)] = upper_bound
      smallest = upper_bound

    self.bound = bounds
    assert len(bounds) == self.c

  def optimise_strict_constraints(self):
    while self.upper_bound - self.lower_bound >= 2:
      self.mid_size = int((self.lower_bound + self.upper_bound) / 2)
      print(f'S Trying {self.lower_bound} < {self.mid_size} < {self.upper_bound}', end='', flush=True)
      assumptions = [-self.rhs[self.mid_size]]
      sat = self.solver.solve(assumptions=assumptions)
      if sat:
        print('\tdown')
        self.upper_bound = self.mid_size
        continue
      else:
        print('\tup')
        self.lower_bound = self.mid_size
        continue
    self.bound = self.upper_bound

  def solve(self):
    print('===============')
    print('Start solving')
    self.optimise_constraints()

    if self.weak:
      assumptions = [-self.rhs[rid][self.bound[rid]] for rid in self.rids if self.bound[rid] < self.N]
      sat = self.solver.solve(assumptions=assumptions)
    else:
      assumptions = [-self.rhs[self.bound]] if self.bound < self.N * self.c else []
      sat = self.solver.solve(assumptions=assumptions)
    assert sat
    # Even omzetten in een makkelijkere data structuur
    m = self.solver.get_model()
    model = {abs(l): l > 0 for l in m}

    # Precieze groottes van elk component tellen
    counts = []
    for rid in self.rids:
      count = 0
      # Eerst verzamelen we de representanten
      for s in self.machine.states:
        if model[self.var_state_rep(rid, s)]:
          count += 1
      counts.append(count)

    print(f'Reduced sizes = {counts} = {sum(counts)}')
    if self.record_file:
      with open(self.record_file, 'a') as file:
        last_two_comps = '/'.join(filename.split('/')[-2:])
        file.write(f'{last_two_comps}\t{self.N}\t{len(self.machine.inputs)}\t{len(self.machine.outputs)}\t{self.weak}\t{self.c}\t{sum(counts)}\t{sorted(counts, reverse=True)}\n')

    projections = {}
    for rid in self.rids:
      local_outputs = self.machine.outputs.copy()
      projections[rid] = {}
      count = 0

      while local_outputs:
        repr = local_outputs.pop()
        if repr in projections[rid]:
          continue

        projections[rid][repr] = f'cls_{rid}_{count}'
        others = False

        for o in local_outputs:
          if model[self.var_rel(rid, o, repr)]:
            others = True
            projections[rid][o] = f'cls_{rid}_{count}'

        if not others:
          # Aangeven dat het een unieke output is
          projections[rid][repr] = f'cls_{rid}_{count}_u'

        count += 1

    print('===============')
    print('Output mapping:')
    print_table(lambda o, rid: projections[rid][o], self.machine.outputs, self.rids)
    self.projections = projections
    print(f'{self.N} = {counts}')

def decompose_weak(machine, c):
  # Als er maar 1 state is, valt er niks te ontbinden, maar het script zou niet
  # moeten crashen. Het aantal outputs moet minstens 3 zijn voor een zinvolle
  # decompositie.
  assert len(machine.states) >= 1
  assert len(machine.inputs) >= 1
  assert len(machine.outputs) >= 1

  print(f'Input FSM: {len(machine.states)} states, {len(machine.inputs)} inputs, and {len(machine.outputs)} outputs')
  with Encoder(machine, c, weak=True) as encoder:
    encoder.solve()
    bound = encoder.bound
    projections = encoder.projections
    rids = encoder.rids
  return bound, projections, rids

def reachable(fsm: FSM):
  result = set()
  todo = [fsm.initial_state]
  while todo:
    cur = todo[0]
    todo = todo[1:]
    if cur in result:
      continue
    result.add(cur)
    for input in fsm.inputs:
      next = fsm.transition(cur, input)
      todo.append(next)
  return result

def find_3_decomposition():
  while True:
    random_size = 4
    random_inputs = ['a', 'b']
    random_outputs = ['x', 'y', 'z']
    bound = 0
    fsm1 = random_fsm(random_size, random_inputs, random_outputs)
    if reachable(fsm1) != set(fsm1.states):
      print(fsm1)
      print("Not all states in fsm1 were reachable!\n\n\n")
      continue
    bound, projections, rids = decompose_weak(fsm1, 3)
    max_bound = max(bound.values())
    fsm2 = random_fsm(3, random_inputs, random_outputs)
    if reachable(fsm2) != set(fsm2.states):
      print(fsm2)
      print("Not all states in fsm2 were reachable!\n\n\n")
      continue
    product = product_fsm(fsm1, fsm2)
    decompose_weak(product, 2) #Check
    product_bound, product_projections, product_rids = decompose_weak(product, 3)
    if max(product_bound.values()) < max_bound:
      print("1: " + str(fsm1))
      print("2: " + str(fsm2))
      print("Product: " + str(product))
      print("Product bound was lower than bound: " + str(max(product_bound.values())) + " < " + str(max_bound))
      print("Projections: " + str(projections))
      print("rids: " + str(rids))
      print("Product projections: " + str(product_projections))
      print("Product rids: " + str(product_rids))
      break
    else:
      print("Product bound was same as bound\n\n\n")


###################################
# Run script
if __name__ == '__main__':
  find_3_decomposition()
  #main()

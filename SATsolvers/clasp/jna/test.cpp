
#include <string>
#include <iostream>

#include "jna_clasp.h"

using namespace std;
using namespace JNA;

static string params = "--seed=2147483647 -n 1";
static char args[1024];
//static string prostr = "p cnf 5 5\n1 -2 0\n1 -2 3 0\n-1 -3 4 0\n2 5 0\n1 -5 -4 0";
//static string prostr = "p cnf 5 3\n1 0\n-2 0\n-1 4 0";
static string prostr = "p cnf 5 3\n1 0\n-2 0\n-1 4 0";

void init()
{
	strcpy(args, params.c_str());
}

void example1()
{
	init();
	cout << "START TEST" << endl;

	JNAConfig* conf = new JNAConfig();
	conf->configure(args);
	cout << conf->getStatus() << endl;
	cout << conf->getErrorMessage() << endl;
	cout << conf->getClaspErrorMessage() << endl;
	cout << "NumModels: " << conf->getConfig()->enumerate.numModels << endl;
	cout << "Problem:\n" << prostr << "\n--------------------" << endl;

	JNAProblem problem(prostr);
	
	JNAResult result;

	Clasp::ClaspFacade libclasp;
	libclasp.solve(problem, *(conf->getConfig()), &result);

	cout << "Assignment:\n" << result.getAssignment() << endl;

	cout << "END TEST" << endl;
}

void example2()
{
	init();
	cout << "START TEST" << endl;
	JNAConfig* conf = (JNAConfig*) createConfig(args, params.length(), 128);
	cout << "Conf Status: " << getConfigStatus(conf) << endl;
	cout << "Conf Error: " << getConfigErrorMessage(conf) << endl;
	cout << "Clasp Error: " << getConfigClaspErrorMessage(conf) << endl;

	// set rand
	

	JNAProblem* problem = (JNAProblem*) createProblem(prostr.c_str());

	JNAResult* result = (JNAResult*) createResult();

	jnasolve(problem, conf, result);
	cout << "Result state: " << getResultState(result) << endl;

	cout << "Assignment:\n" << getResultAssignment(result) << endl;
	
	destroyConfig(conf);
	destroyConfig(problem);
	destroyConfig(result);
	cout << "END TEST" << endl;
}
int main()
{
	example2();
}

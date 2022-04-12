import java.util.concurrent.Semaphore;
import java.util.Random;

public class Bank {
	

	static Semaphore tellers = new Semaphore(3);//used to determine if tellers are available
	static Semaphore manager = new Semaphore(1);//manager interaction
	static Semaphore safeUse = new Semaphore(2);//determines how many tellers are in the safe
	static Semaphore enter = new Semaphore(10);//line limit for customers
	static Semaphore selectTeller = new Semaphore(1);//allows a customer to choose a teller one at a time.
	static Semaphore bank = new Semaphore(0);//for check thread to close the bank.
	
	static final int TELLER_COUNT = 3;//determines teller count
	static final int CUSTOMER_COUNT = 100;//determines customer count
	
	static boolean done = false;//used for checks if all customers have been served
	static boolean allLeft = false;//used for checks if all tellers have left the bank
	
	static int[] teller = new int[TELLER_COUNT];//determines which customer that each teller is handling.
	static boolean[] teller_transaction = new boolean[TELLER_COUNT];//determines what transaction each teller will handle.
	static boolean[] served = new boolean[CUSTOMER_COUNT];//default false array for customers to determine if they have been served.
	static boolean[] left = new boolean[TELLER_COUNT];//default false array for tellers to determine if they have left the bank.
	
	static Semaphore[] waitingForCustomer = new Semaphore[TELLER_COUNT];//block for each teller so they can wait for customers.
	static Semaphore[] waitTurn = new Semaphore[TELLER_COUNT*2];//creates 2 way chat interaction between teller and customer for each teller
	
	public static void main(String args[]) {
		Teller[] tellList = new Teller[TELLER_COUNT];
		Customer[] cusList = new Customer[CUSTOMER_COUNT];
		int idTCount=0;//sets id for tellers
		int idcCount=0;//sets id for customers
		
		for(int i = 0; i < TELLER_COUNT; i++) {//creates a waiting for customer semaphore for each teller
			waitingForCustomer[i] = new Semaphore(0);
		}
		for(int i = 0; i < TELLER_COUNT*2; i++) {//creates interaction turns for each teller
			waitTurn[i] = new Semaphore(0);
		}

		for(int i = 0; i < TELLER_COUNT; i++) {//create x number of teller threads that will handle customers
			tellList[i] = new Teller(idTCount++);//creates a new teller thread with incrementing ID
			tellList[i].start();//starts teller thread
		}
		for(int i =0; i < CUSTOMER_COUNT;i++) {//create x number of customer threads that will enter the bank at any time.
			cusList[i] = new Customer(idcCount++,(int)(Math.random()*5000)); //creates a new customer thread with incrementing ID and random time.
			cusList[i].start();//starts teller thread
		}
		
		Ending check = new Ending();//creates check thread that will run when the bank closes
		check.start();//starts check thread
		
		
	}
	
	public static boolean areAllTrue(){
	    for(boolean b : served) if(!b) return false;
	    done = true;
	    for(int i = 0; i < TELLER_COUNT; i++) {
	    	waitingForCustomer[i].release();
	    }
	    return true;
	}
	
	public static void closing() {//will check if all tellers have left the bank
		allLeft = true;//sets condition to check. if any teller is still in the bank, this will turn false
		for(int i = 0; i < TELLER_COUNT; i++) {
			if(!left[i]) {//checks if all tellers left
				allLeft = false;
			}
		}
		if(allLeft) {//if all tellers have left, will close the bank.
			bank.release();
		}
	}
	
	public static class Customer extends Thread{
		int id;
		int time;
		boolean depOrWith;//true is withdraw, false is deposit
		public Customer(int i,int time) {
			this.time = time; 
			this.id = i;
			this.depOrWith = new Random().nextBoolean();
		}
		
		public void run() {

			//if the boolean is true, then the Customer will want to perform a withdrawal transaction
			if(depOrWith) {
				System.out.println("Customer " + id + " wants to perform a withdrawal transaction.");
			}//if the boolean is false, the Customer will want a deposit transaction
			else {
				System.out.println("Customer " + id + " wants to perform a deposit transaction.");
			}
			
			try {
				
				//sets local teller_id to -1 since no teller has been selected yet
				int teller_id = -1;
				
				Thread.sleep(time);//based on the randomly generated time, the thread will wait to simulate the entering time
				
				enter.acquire();//customer will enter the building and get in line
				System.out.println("Customer "+ id + " is going to the bank.");
				System.out.println("Customer "+ id + " is getting in line.");
				
				tellers.acquire();//will attempt to find a teller and wait until there is a teller available.
				System.out.println("Customer "+ id + " is selecting a teller.");
				
				selectTeller.acquire();//goes through the teller selection process
				for(int j = 0; j < TELLER_COUNT; j++) {//runs through tellers to find available teller
					if(teller[j] == -1) {//finds teller and sets id
						teller[j] = id;
						teller_id = j;
						System.out.println("Customer "+ id + " goes to Teller " + j + ".");
						
						waitingForCustomer[j].release();//releases teller thread and starts introduction
						System.out.println("Customer " + id + " introduces itself to Teller " + j + ".");
						
						break;
					}
				}
				selectTeller.release();//finishes teller process
				waitTurn[teller_id*2].release();//sets interaction turn
				waitTurn[(teller_id*2)+1].acquire();
				
				//checks transaction type and sets it in the static transaction array
				if(depOrWith) {
					teller_transaction[teller_id] = true;
					System.out.println("Customer " + id + " asks for a withdrawal transaction.");
				}
				else {
					teller_transaction[teller_id] = false;
					System.out.println("Customer " + id + " asks for a deposit transaction.");
				}
				waitTurn[teller_id*2].release();//sets interaction turn
				waitTurn[teller_id*2+1].acquire();
				
				System.out.println("Customer " + id + " thanks Teller " + teller_id + " and leaves.");
				served[id] = true;//onces customer has thanked teller and left, sets served to true for that customer
				
				tellers.release();//releases teller to serve a new customer
				teller[teller_id] = -1;
				waitTurn[teller_id*2].release();//releases wait for teller
				
				enter.release();//leaves the building
				
				
			} catch (InterruptedException e) {
				System.err.println("Error in Customer Thread " + id + ": " + e);
			}
		}
	}

	public static class Teller extends Thread{
		int id;
		public Teller(int i) {
			this.id = i;
		}
		public void run() {
				try {
					teller[id] = -1;//sets customer id to -1 for customers to find teller
					System.out.println("Teller " + id + " is ready to serve.");
					System.out.println("Teller " + id + " is waiting for a customer.");
					waitingForCustomer[id].acquire();//waits for customer
					
					if(done) {//in the case that all customers have been served, will start the closing process
						System.out.println("Teller " + id + " is leaving for the day.");
						left[id] = true;
						closing();//checks if all tellers have left the bank
						return ;//ends thread
					}
					
					waitTurn[id*2].acquire();//waits for introduction of customer
					System.out.println("Teller " + id + " is serving Customer " + teller[id] + ".");
					waitTurn[id*2+1].release();//sets interaction turn
					waitTurn[id*2].acquire();
					
					//determines which type of transaction the teller will handle
					System.out.println("Teller " + id + " is handling the " + ((teller_transaction[id]) ? ("withdrawal ") : ("deposit ")) + "request.");
					if(teller_transaction[id]) {//runs if teller is handling a withdrawal transaction
						System.out.println("Teller " + id + " is going to the manager.");
						manager.acquire();//talking to manager
						System.out.println("Teller " + id + " is getting the manager's permission.");
						Thread.sleep((int)((Math.random()*25)+5));//simulates manager interaction
						manager.release();//ends interaction with manager
						System.out.println("Teller " + id + " is going to the safe.");
						safeUse.acquire();//enters safe
						System.out.println("Teller " + id + " is in the safe.");
						Thread.sleep((int)((Math.random()*40)+10));//simulates safe interaction
						System.out.println("Teller " + id + " is leaving the safe.");
						safeUse.release();//leaves safe
					}
					else {//runs if teller is handling a deposit transaction
						System.out.println("Teller " + id + " is going to the safe.");
						safeUse.acquire();//enters safe
						System.out.println("Teller " + id + " is in the safe.");
						Thread.sleep((int)((Math.random()*40)+10));//simulates safe interaction
						System.out.println("Teller " + id + " is leaving the safe.");
						safeUse.release();//leaves safe
					}
					//ends interaction with customer
					System.out.println("Teller " + id + " finishes Customer " + teller[id] + "\'s " + ((teller_transaction[id]) ? ("withdrawal ") : ("deposit ")) + "transaction.");
					waitTurn[id*2+1].release();//sets interaction turn
					waitTurn[id*2].acquire();
					
					if(!areAllTrue()) {//checks if all customers have been served, if they havent been all served, will rerun teller to serve a new customer
						this.run();	
					}
					else{//if all customers have been served, run this code
						System.out.println("Teller " + id + " is leaving for the day.");
						left[id] = true;//teller leaves the bank
						closing();//checks if all tellers have left the bank
						return ;//ends thread
					}
					
				} catch (InterruptedException e) {
					System.err.println("Error in Teller Thread " + id + ": " + e);
				}
			
		}
		
		
	}
	
	public static class Ending extends Thread{
		int id;
		public Ending() {
			this.id = 370;//id is hard coded but used for debuging 
		}
		public void run() {
			try {
				bank.acquire();//opens the bank, waits for release to close the bank
				System.out.println("The bank closes for the day.");
			} catch (InterruptedException e) {
				System.err.println("Error in Bank Thread " + id + ": " + e);
			}
		}
	}
}


